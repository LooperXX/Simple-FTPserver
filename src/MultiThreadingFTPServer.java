import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/* MultiThreadingFTPServer */

/**
 * This class complete the function of MultiThreadingFTPServer.
 *
 * @author LooperXX
 */

public class MultiThreadingFTPServer {
    public static void main(String[] args) throws IOException {
        ServerSocket controller = null;
        try {
            controller = new ServerSocket(2333); /*服务器端创建监听特定端口的ServerSocket，负责接收客户连接请求*/
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        while (true) {
            try {
                Socket contSocket = controller.accept();/*实现多线程*/
                new FTPServer(contSocket).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
