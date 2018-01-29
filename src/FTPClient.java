import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.StringTokenizer;

/* FTPClient */

/**
 * This class complete the function of FTPClient.
 *
 * @author LooperXX
 */

public class FTPClient {
    private static Socket controller; /*命令Socket*/
    private static Socket transfer;/*数据Socket*/
    private static int controlPort = 0;/*命令端口初始化*/
    private static int dataPort = 0;/*数据端口初始化*/
    private static String serverAddress;/*服务器的IP地址*/
    private static String rootStr = "./";/*客户端的根目录*/
    private static Boolean folderStorLoop = false;  /*上传文件夹*/
    private static String[] folderStor;
    private static int k = 0;
    private static Boolean folderRetrLoop = false; /*下载文件夹*/
    private static String[] folderRetr;
    private static int r = 0;
    private static long lengthRetr = 0;/*断点续传所需数据*/
    private static long lengthStor = 0;
    private static File currentDirectory = null;
    private static File currentFile = null;

    private static String storeFolder(File[] folder) {  /*上传文件夹内的文件名提取*/
        String str = "";
        for (File folderFile : folder) {
            str += folderFile.getName() + "^";
        }
        return str;
    }

    public static void main(String[] args) throws IOException {
        /*客户端和 FTP 服务器建立 Socket 连接*/
        while (true) {
            Scanner scanner = new Scanner(System.in);
            System.out.println("[Client] Please Input Sever Address:");
            serverAddress = scanner.nextLine();
            System.out.println("[Client] Please Input Server Port:");
            controlPort = scanner.nextInt();
            try {
                controller = new Socket(serverAddress, controlPort);
                break;/*成功则跳出循环*/
            } catch (Exception e) {
                System.out.println("[Client] Connection Failed! Please Input Message Correctly!");/*不成功则输出提示并开始新一轮输入*/
            }
        }
        System.out.println("[Client] Connected");/*提示成功连接*/
        /*处理连接后的用户需求*/
        String inst;/*客户端的用户输入*/
        String response;/*服务器的回应*/
        BufferedReader instReader = new BufferedReader(new InputStreamReader(controller.getInputStream()));/*即服务器的输出流*/
        PrintWriter instWriter = new PrintWriter(new OutputStreamWriter(controller.getOutputStream()));/*服务器的输出流，即客户端的输入流*/
        while (true) {                        /*客户端与服务器开始交流*/
            if (folderStorLoop) {/*是否存在上传文件夹的STOR指令序列*/
                inst = folderStor[--k];
                if (k == 0) {/*还原数据*/
                    folderStor = null;
                    folderStorLoop = false;
                }
            } else if (folderRetrLoop) {/*是否存在下载文件夹的RETR指令序列*/
                inst = folderRetr[--r];
                if (r == 1) {/*还原数据*/
                    inst = folderRetr[--r];/*跳过无用数据“[Server] ”*/
                    folderRetr = null;
                    folderRetrLoop = false;
                }
            } else {
                Scanner scanner = new Scanner(System.in);
                inst = scanner.nextLine();
                rootStr = "./";/*无上传下载文件夹指令序列时，强制还原客户端根目录*/
            }
            if (inst != null && !inst.equals("")) {
                if (inst.equals("QUIT")) { /*如果客户端的用户键入了QUIT指令*/
                    instWriter.println(inst);
                    instWriter.flush();/*客户端传给服务器*/
                    controller.close();/*关闭命令端口*/
                    if (transfer != null) { /*如果打开了数据端口，则关闭*/
                        transfer.close();
                    }
                    break;/*跳出while循环，结束本次连接*/
                } else {
                    StringTokenizer str = new StringTokenizer(inst);/*使用StringTokenizer处理inst字符串*/
                    String instHeader = str.nextToken();
                    String instPara = str.hasMoreTokens() ? str.nextToken() : "";
                    switch (instHeader) {
                        case "USER": /*登录指令*/
                            instWriter.println(inst);
                            instWriter.flush();
                            response = instReader.readLine();
                            System.out.println(response);
                            break;/*跳出switch语句*/
                        case "PASS": /*密码*/
                            instWriter.println(inst);
                            instWriter.flush();
                            response = instReader.readLine();
                            System.out.println(response);
                            if (response.equals("[Server] Wrong Password! Connection Closed!")) {
                                controller.close();
                                System.exit(0);
                            }
                            break;
                        case "PASV": /*被动模式*/
                            instWriter.println(inst);
                            instWriter.flush();
                            response = instReader.readLine();
                            try {
                                dataPort = Integer.parseInt(response);
                                transfer = new Socket(serverAddress, dataPort);/*创建服务器数据端口连接到服务器的指定IP与端口*/
                                System.out.println("[Server] Data Transfer Port: " + dataPort);
                            } catch (NumberFormatException e) {
                                System.out.println(response);
                            }
                            break;
                        case "LIST": /*打印服务器根目录下的文件目录*/
                            instWriter.println(inst);
                            instWriter.flush();
                            response = instReader.readLine();
                            if (response != null) {
                                System.out.println(response.replace('^', '\n'));
                            }
                            break;
                        case "CWD":
                            instWriter.println(inst);
                            instWriter.flush();
                            response = instReader.readLine();
                            if (response.equals("OK")) {
                                System.out.println("[Server] OK");
                            } else System.out.println(response);
                            break;
                        case "RETR":
                            /*判断是否曾下载过*/
                            Boolean hasFile = false;
                            currentDirectory = new File(rootStr);
                            File[] list = currentDirectory.listFiles();
                            if (list != null) {
                                for (File file : list) {
                                    if (file.getName().equals(instPara)) {
                                        hasFile = true;
                                        currentFile = file;
                                        lengthRetr = file.length();
                                        break;
                                    }
                                }
                            }
                            if (hasFile) {/*下载过则先发送REST指令*/
                                instWriter.println("REST " + lengthRetr);
                                instWriter.flush();
                            }
                            instWriter.println(inst);
                            instWriter.flush();
                            response = instReader.readLine();
                            if (response.equals("OK")) {
                                System.out.println("[Server] OK");
                                byte[] inputByte = new byte[1024];
                                int length = 0;
                                DataInputStream dataIn = new DataInputStream(transfer.getInputStream());   /*接受服务器的数据输入流*/
                                FileOutputStream fileOut;
                                if (lengthRetr == 0) {
                                    fileOut = new FileOutputStream(new File(rootStr + dataIn.readUTF()));
                                } else {
                                    fileOut = new FileOutputStream(currentFile, true);/*如果要断点续传下载，则续写原文件并还原lengthRetr*/
                                    dataIn.readUTF();
                                    lengthRetr = 0;
                                }
                                System.out.println("[Client] Receiving...");
                                while (true) { /*完成传输*/
                                    if (dataIn != null) {
                                        length = dataIn.read(inputByte, 0, inputByte.length);
                                    }
                                    if (length == -1) {
                                        break;
                                    }
                                    fileOut.write(inputByte, 0, length);
                                    fileOut.flush();
                                }
                                System.out.println("[Client] Complete!");
                                fileOut.close();
                                dataIn.close();
                                transfer = new Socket(serverAddress, dataPort);
                            } else if (response.equals("[Server] Is a Directory!")) {/*如果下载的是文件夹，则新建文件夹并作为客户端根目录*/
                                folderRetrLoop = true;
                                rootStr = "./" + instPara + "/";
                                boolean clientRoot = new File(rootStr).mkdirs();
                                if (clientRoot) {
                                    System.out.println("[Client] New folder has been created!");
                                }
                                instWriter.println("CWD ./" + instPara); /*找到服务器中待下载的文件夹并作为新的根目录*/
                                instWriter.flush();
                                response = instReader.readLine();
                                if (response.equals("OK")) {
                                    instWriter.println("LIST");
                                    instWriter.flush();
                                    response = instReader.readLine();
                                    StringTokenizer folderList = new StringTokenizer(response, "^");
                                    String folderInList;
                                    folderRetr = new String[folderList.countTokens() + 1];
                                    folderRetr[r++] = "CWD ../";/*还原服务器工作目录为原根目录*/
                                    while (folderList.hasMoreTokens()) {/*加载文件夹中待下载的文件目录，生成RETR指令序列*/
                                        folderInList = folderList.nextToken();
                                        inst = "RETR " + folderInList;
                                        folderRetr[r++] = inst;
                                    }

                                }
                            } else System.out.println(response);
                            break;
                        case "STOR":
                            Boolean resume = false;
                            instWriter.println(inst);
                            instWriter.flush();
                            response = instReader.readLine();
                            if (!response.equals("OK") && response.substring(0, 5).equals("Exist")) {
                                response = response.replace("Exist ", "");/*提取服务器信息中已上传的字节数*/
                                lengthStor = Long.parseLong(response);
                                resume = true;
                                response = "OK";
                            }
                            if (response.equals("OK")) {
                                File file = new File(rootStr + instPara);/*上传的文件应存在于客户端当前根目录下*/
                                if (file.exists() && !file.isDirectory()) {
                                    instWriter.println("START");
                                    instWriter.flush();
                                    DataOutputStream dataOut = new DataOutputStream(transfer.getOutputStream()); /*客户端的输出流，即服务器的输入流*/
                                    FileInputStream fileIn = new FileInputStream(file);
                                    if (resume) {/*如果需要断点续传，则skip过lengthStor的长度，并还原lengthStor*/
                                        fileIn.skip(lengthStor);
                                        lengthStor = 0;
                                    }
                                    byte[] sendByte = new byte[1024];
                                    int length;
                                    System.out.println("[Client] Sending...");
                                    dataOut.writeUTF(file.getName());
                                    while ((length = fileIn.read(sendByte, 0, sendByte.length)) >= 0) {
                                        dataOut.write(sendByte, 0, length);
                                        dataOut.flush();
                                    }
                                    System.out.println("[Client] Complete!");
                                    dataOut.close();
                                    fileIn.close();
                                    transfer = new Socket(serverAddress, dataPort);
                                } else {
                                    if (file.isDirectory()) { /*如果上传的是文件夹*/
                                        instWriter.println("WAIT");
                                        instWriter.flush();
                                        rootStr = "./" + instPara + "/";/*修改客户端根目录为待上传的文件夹*/
                                        File[] folder = file.listFiles();
                                        String listf = storeFolder(folder);
                                        StringTokenizer folderList = new StringTokenizer(listf, "^");
                                        String folderInList;
                                        folderStor = new String[folderList.countTokens() + 2];
                                        folderStor[k++] = "CWD ../";/*将服务器目录还原为其根目录*/
                                        while (folderList.hasMoreTokens()) { /*加载文件夹中待上传的文件目录，生成STOR指令序列*/
                                            folderInList = folderList.nextToken();
                                            inst = "STOR " + folderInList;
                                            folderStorLoop = true;
                                            folderStor[k++] = inst;
                                        }
                                        folderStor[k++] = "CWD ./" + instPara;/*在服务器中新建一个同名文件夹，并设置为服务器根目录*/
                                    } else {
                                        System.out.println("[Client] File Not Exist");
                                    }
                                }
                            } else {
                                System.out.println(response);
                            }
                            break;
                        default:
                            System.out.println("[Client] Wrong Instruction!");
                    }
                }
            }
        }
    }
}

