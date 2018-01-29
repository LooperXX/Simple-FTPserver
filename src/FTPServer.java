import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import java.util.StringTokenizer;

/* FTPServer */

/**
 * This class complete the function of FTPServer.
 *
 * @author LooperXX
 */

public class FTPServer extends Thread {   /*通过扩展Thread类的方式实现多线程*/
    /*状态布尔参数初始化*/
    private boolean loginStatus = false;
    private boolean authStatus = false;
    private boolean dataStatus = false;
    private boolean closeStatus = false;
    /*其余初始化*/
    private ServerSocket transfer = null;
    private Socket contSocket = null;/*客户端的命令端口*/
    private Socket transSocket = null;/*客户端的数据端口*/
    private String instHeader = "";
    private String instPara = "";
    private String strDir = "C:\\Users\\HP\\Desktop\\计网实验\\FTP";/*服务器根目录*/
    private static File currentDirectory = null;/*文件操作*/
    private static File currentFile = null;
    private static int dataPort = 0;
    private static long lengthRetr = 0;/*断点续传*/
    private static long lengthStor = 0;
    private Random random = new Random();

    public FTPServer(Socket contSocket) {
        this.contSocket = contSocket;
    }

    @Override
    public void run() throws NullPointerException {
        String clientInst;  /*客户端的命令信息*/
        try {
            BufferedReader instReader = new BufferedReader(new InputStreamReader(contSocket.getInputStream()));/*命令输入流*/
            PrintWriter instWriter = new PrintWriter(new OutputStreamWriter(contSocket.getOutputStream()));/*命令输出流*/
            while ((clientInst = instReader.readLine()) != null) {
                System.out.println("[Client] " + clientInst);
                StringTokenizer strInst = new StringTokenizer(clientInst);
                instHeader = strInst.nextToken();/*客户端指令分析*/
                instPara = strInst.hasMoreTokens() ? strInst.nextToken() : "";
                switch (instHeader) {
                    case "USER":
                        if (instPara.equals("root")) {
                            loginStatus = true;
                            instWriter.println("[Server] Logging in... Username: " + instPara);
                            instWriter.flush();
                        } else {
                            instWriter.println("[Server] No Such Users!");
                            instWriter.flush();
                        }
                        break;
                    case "PASS":
                        if (loginStatus) {
                            if (instPara.equals("root")) {
                                //loginStatus = false; /*重置登录状态*/
                                authStatus = true;
                                instWriter.println("[Server] OK!");
                                instWriter.flush();
                            } else {
                                instWriter.println("[Server] Wrong Password! Connection Closed!");
                                instWriter.flush();
                                closeStatus = true;
                            }
                        } else {
                            instWriter.println("[Server] Operation Refused!");
                            instWriter.flush();
                        }
                        break;
                    case "PASV":
                        if (!authStatus) {
                            instWriter.println("[Server] Operation Refused!");
                            instWriter.flush();
                            transfer = null;
                            break;
                        }
                        if (transfer != null) {
                            instWriter.println("[Server] Connection Already Set!");
                            instWriter.flush();
                        } else {
                            while (true) {
                                int portHigh = 1 + random.nextInt(20);
                                int portLow = 100 + random.nextInt(1000);
                                dataPort = portHigh * 256 + portLow;/*随机端口分配*/
                                try {
                                    transfer = new ServerSocket(dataPort); /*创建服务器本地端口成为对客户端的数据端口*/
                                    currentDirectory = new File(strDir);
                                    dataStatus = true;
                                    break;
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            instWriter.println(dataPort + "");
                            instWriter.flush();
                            transSocket = transfer.accept();/*客户端的数据端口*/
                        }
                        break;
                    case "LIST":
                        if (!dataStatus) {
                            instWriter.println("[Server] Operation Refused!");
                            instWriter.flush();
                            break;
                        }
                        if (currentDirectory.isDirectory()) {
                            File[] list = currentDirectory.listFiles();/*返回一个包含文件目录的数组*/
                            String str = "[Server] ^";
                            str += folderPrint(list);/*调用folderPrint方法*/
                            instWriter.println(str);
                            instWriter.flush();
                        } else {
                            instWriter.println("[Server] System Error!");
                            instWriter.flush();
                        }
                        break;
                    case "CWD":
                        if (!dataStatus) {
                            instWriter.println("[Server] Operation Refused!");
                            instWriter.flush();
                            break;
                        }
                        StringTokenizer paths = new StringTokenizer(instPara, "/");
                        String path = "";
                        boolean error = false;
                        while (paths.hasMoreTokens()) {
                            path = paths.nextToken();
                            if (path.equals("..")) {
                                if (strDir.equals("C:\\Users\\HP\\Desktop\\计网实验\\FTP")) {  /*如果对原根目录的父级目录访问，则拒绝*/
                                    instWriter.println("[Server] Permission Denied!");
                                    instWriter.flush();
                                    error = true;
                                    break;
                                } else { /*否则，则进入当前根目录的父级目录中*/
                                    strDir = strDir.substring(0, strDir.lastIndexOf("/"));
                                }
                            } else if (!path.equals(".")) {
                                strDir += "/" + path;
                            }
                        }
                        if (error) break;
                        currentDirectory = new File(strDir);
                        if (!currentDirectory.isDirectory()) {
                            boolean serverRoot = new File(strDir).mkdirs();/*新建文件夹*/
                            if (serverRoot) {
                                instWriter.println("[Server] New folder has been created!");
                                instWriter.flush();
                            }
                        } else {
                            instWriter.println("OK");
                            instWriter.flush();
                        }
                        break;
                    case "RETR":
                        if (!dataStatus) {
                            instWriter.println("[Server] Operation Refused!");
                            instWriter.flush();
                            break;
                        }
                        if (currentDirectory.isDirectory()) {
                            /*当前根目录下待下载文件的定位*/
                            File[] list = currentDirectory.listFiles();
                            boolean hasFile = false;
                            if (list != null) {
                                for (File file : list) {
                                    if (file.getName().equals(instPara)) {
                                        currentFile = file;
                                        hasFile = true;
                                        break;
                                    }
                                }
                                if (!hasFile) {
                                    instWriter.println("[Server] No such file.");
                                    instWriter.flush();
                                } else {
                                    if (currentFile.isDirectory()) {
                                        instWriter.println("[Server] Is a Directory!");/*提示客户端建立文件夹下载指令序列*/
                                        instWriter.flush();
                                    } else {
                                        instWriter.println("OK");
                                        instWriter.flush();
                                        DataOutputStream dataOut = new DataOutputStream(transSocket.getOutputStream());/*数据输出流*/
                                        FileInputStream fileIn = new FileInputStream(currentFile); /*文件数据输入流*/
                                        if (lengthRetr != 0) {/*如果需要断点续传，则skip过lengthRetr的长度，并还原lengthRetr*/
                                            fileIn.skip(lengthRetr);
                                            lengthRetr = 0;
                                        }
                                        byte[] sendByte = new byte[1024];
                                        int length = 0;
                                        dataOut.writeUTF(currentFile.getName());
                                        while ((length = fileIn.read(sendByte, 0, sendByte.length)) >= 0) {/*完成传输*/
                                            dataOut.write(sendByte, 0, length);
                                            dataOut.flush();
                                        }
                                        dataOut.close();
                                        fileIn.close();
                                        transSocket = transfer.accept();
                                    }
                                }
                            } else {
                                instWriter.println("[Server] System Error!");
                                instWriter.flush();
                            }
                        } else {
                            instWriter.println("[Server] System Error!");
                            instWriter.flush();
                        }
                        break;

                    case "STOR":
                        boolean hasFile = false;
                        if (dataStatus) {
                            if (currentDirectory.isDirectory()) {/*判断待上传文件是否为断点续传*/
                                File[] list = currentDirectory.listFiles();
                                if (list != null) {
                                    for (File file : list) {
                                        if (file.getName().equals(instPara)) {
                                            currentFile = file;
                                            hasFile = true;
                                            lengthStor = currentFile.length();
                                            break;
                                        }
                                    }
                                }
                            }
                            if (hasFile) {/*如果是断点续传，则向客户端发送已上传字节数*/
                                instWriter.println("Exist " + currentFile.length());
                                instWriter.flush();
                            } else {
                                instWriter.println("OK");
                                instWriter.flush();
                            }
                            String response;
                            if ((response = instReader.readLine()).equals("START")) {
                                byte[] inputByte = new byte[1024];
                                int length = 0;
                                DataInputStream dataIn = new DataInputStream(transSocket.getInputStream());
                                FileOutputStream fileOut;
                                if (lengthStor == 0) {
                                    fileOut = new FileOutputStream(new File(strDir + "/" + dataIn.readUTF()));
                                } else { /*如果需要断点续传，则skip过lengthStor的长度，并还原lengthStor*/
                                    fileOut = new FileOutputStream(currentFile, true);
                                    lengthStor = 0;
                                    dataIn.readUTF();
                                }
                                while ((length = dataIn.read(inputByte, 0, inputByte.length)) >= 0) { /*完成传输*/
                                    fileOut.write(inputByte, 0, length);
                                    fileOut.flush();
                                }
                                fileOut.close();
                                dataIn.close();
                                transSocket = transfer.accept();
                            } else if (response.equals("WAIT")) {
                                break;
                            }
                        } else {
                            instWriter.println("[Server] Operation Refused!");
                            instWriter.flush();
                        }
                        break;
                    case "REST":
                        lengthRetr = Long.parseLong(instPara);
                        break;
                    case "QUIT":
                        closeStatus = true;
                        break;
                    default:
                        instWriter.println("[Server] Wrong Operation!");
                        instWriter.flush();
                }
                if (closeStatus) break;
            }
            resetSocket();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String folderPrint(File[] folder) {
        String folderStr = ""; /*当前文件夹的目录*/
        if (folder == null) {
            folderStr = "";
        } else {
            for (int i = 0; i < folder.length; i++) { /*遍历该数组。完成文件夹与文件的递归处理*/
                if (folder[i].isDirectory()) {
                    folderStr += "----";
                    folderStr += "\t" + folder[i].getName() + "\t\t\tFOLDER----\t^"; /*文件夹名*/
                    File[] folderList = folder[i].listFiles();
                    folderStr += folderPrint(folderList);
                    folderStr += "-------------------------------" + "^";
                } else {
                    folderStr += "\t" + folder[i].getName() + "^";  /*文件名*/
                }
            }
        }
        return folderStr;
    }

    private void resetSocket() throws IOException, NullPointerException {
        System.out.println("Closing...");
        contSocket.close();
        if (transSocket != null) {
            transSocket.close();
            transfer.close();
            transSocket = null;
            transfer = null;
        }
        loginStatus = false;
        authStatus = false;
        closeStatus = false;
        dataStatus = false;
        contSocket = null;
        instHeader = "";
        instPara = "";
        strDir = "C:\\Users\\HP\\Desktop\\计网实验\\FTP";
        currentDirectory = null;
        currentFile = null;
        dataPort = 0;
    }
}
