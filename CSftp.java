import com.sun.org.apache.xerces.internal.impl.xpath.regex.Match;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CSftp {

    static final int MAX_LEN = 255;
    static final int ARG_COUNT = 2;
    static final int DEFAULT_PORTNUM = 21;
    static final String REQUEST_PREFIX = " --> ";
    static final String RESPONSE_PREFIX = " <-- ";

    // data connection variables
    static Socket dataSocket;
    static InputStream dataInStream;
    static BufferedReader dataIn;

    static Socket socket;
    static PrintWriter out;
    static BufferedReader in;

    public static void main (String [] args) {

        String hostName;
        int portNumber;

        // Get command line arguments and connected to FTP
        // If the arguments are invalid or there aren't enough of them
        // then exit with usage: cmd ServerAddress ServerPort
        // check if arguments input is valid
        if (args.length == 2) {
            hostName = args[0];
            portNumber = Integer.parseInt(args[1]);
        } else if( args.length == 1) {        // if only IP is provided, give default port number
            hostName = args[0];
            portNumber = DEFAULT_PORTNUM;
        } else {                             // incorrect number of arguments provided
            System.err.println("Usage: cmd ServerAddress ServerPort");
            return;
        }

        try {

            // initialise socket.
            socket = new Socket(hostName, portNumber);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println(RESPONSE_PREFIX + in.readLine());

            while (true) {
                System.out.print("csftp> ");
                //take user input
                Scanner userCommand = new Scanner(System.in);
                String input = userCommand.nextLine();
                //parse string into string array

                String[] userInput = input.split("\\s");

                switch(userInput[0]){
                    case "user":
                        handleUser(userInput);
                        break ;
                    case "pw":
                        handlePw(userInput);
                        break ;
                    case "quit":
                        handleQuit(userInput);
                        break ;
                    case "get":
                        handleGet(userInput);
                        break ;
                    case "features":
                        handleFeatures(userInput);
                        break ;
                    case "cd":
                        handleCd(userInput);
                        break ;
                    case "dir":
                        handleDir(userInput);
                        break ;
                    default:
                        System.err.println("0x001 Invalid command");
                }
            }

        } catch (IOException exception) {
            System.err.println("0xFFFF Processing Error. Input error while reading commands");
        }
    }

    /**
     * This method handles the user command, first checks if correct number of arguments are given then
     * send the command user and the input username to the socket.
     *
     * @param userInput the "user" command and the input username
     */
    public static void handleUser(String[] userInput){
        // the user input should only contain 2 arguments, the user command and the username
        if (userInput.length != 2) {
            inputError();
            return;
        }

        // send request to server
        System.out.println(REQUEST_PREFIX + "USER " + userInput[1]);
        out.println("USER " + userInput[1]);
        out.flush();

        // retrieve response from server
        try {
            System.out.println(RESPONSE_PREFIX + in.readLine());
        } catch (IOException e) {
            System.err.println("0xFFFF Processing error. Unable to read response.");
        }
    }


    /**
     * This method handles the pw command, first checks if correct number of arguments are given then
     * send the command ow and the input pw to the socket.
     *
     * @param userInput the "user" command and the input username
     */
    public static void handlePw(String[] userInput){
        if (userInput.length != 2) {
            inputError();
            return;
        }
        System.out.println(REQUEST_PREFIX + "PASS " + userInput[1]);
        out.println("PASS " + userInput[1]);
        out.flush();
        try {
            System.out.println((RESPONSE_PREFIX + in.readLine()));
        } catch (IOException e) {
            System.err.println("0xFFFF Processing error. Unable to read response.");
        }
    }

    /**
     * This method closes the socket, with exit status 0
     *
     * @param userInput the command quit
     */
    public static void handleQuit(String[] userInput){
        if (userInput.length != 1) {
            inputError();
            return;
        }
        System.out.println(REQUEST_PREFIX + "quit");
        out.println(userInput[0]);
        try {
            System.out.println(RESPONSE_PREFIX + in.readLine());
        } catch (IOException e) {
            System.err.println("0xFFFF Processing error. Unable to read response.");
        }

        // close socket

        closeSocket();
        System.exit(0);
    }

    /**
     * This method retrieves remote files, first by establishing a data connection
     * then using a file output stream to write the requested file into the local directory
     * if the file exists.
     *
     * @param userInput the command and file that the user wishes to retrieve
     */
    public static void handleGet(String[] userInput){
        if (userInput.length != 2) {
            inputError();
            return;
        }
        System.out.println(REQUEST_PREFIX + userInput[0] + " " + userInput[1]);
        establishDataConnection();
        System.out.println(REQUEST_PREFIX + "RETR " + userInput[1]);

        out.println("RETR " + userInput[1]);
        out.flush();
        try {
            String fileResponse = in.readLine();
            System.out.println(RESPONSE_PREFIX + fileResponse);
            // if file does not exist, close data socket.
            if(fileResponse.contains("550")){
                dataSocket.close();
                return;
            }
        } catch (IOException e) {
            System.err.println("0xFFFF Processing error. Unable to read response.");
        }
        // initialise file output stream
        FileOutputStream fs = null;
        try {
            // write file to local directory
            fs = new FileOutputStream(userInput[1]);
            int writeBytes;
            while ((writeBytes = dataInStream.read()) != -1) {
                fs.write(writeBytes);
            }
        } catch (IOException e) {
            System.err.println("0x38E Access to local file " + userInput[1] + " denied.");
        } finally {
            // if the file output stream is not null, close
            if (fs != null) {
                try {
                    fs.close();
                } catch (IOException e) {
                    System.err.println("0xFFFF Processing error. Unable to close file output stream.");
                }
            }
        }
        try {
            System.out.println(RESPONSE_PREFIX + in.readLine());
        } catch (IOException e) {
            System.err.println("0xFFFF Processing error. Unable to read response.");
        }

        // close the data socket
        try {
            dataSocket.close();
        } catch (IOException e) {
            System.err.println("0xFFFF Processing error. Unable to close data socket.");
        }
    }

    /**
     * This method goes into passive mode and retrieves a host IP and port number to
     * establish a socket to either retrieve files, or to check the directory
     */
    private static void establishDataConnection() {

        // enter pasv mode
        out.println("PASV");
        String pasvResponse = null;
        try {
            pasvResponse = in.readLine();
        } catch (IOException e) {
            System.err.println("0xFFFF Processing error. Unable to read response.");
        }
        System.out.println(RESPONSE_PREFIX + pasvResponse);

        // get index of the parentheses from pasvresponse
        int indexOfOpen = pasvResponse.indexOf('(');
        int indexOfClose = pasvResponse.indexOf(')');

        // checks if pasv response is valid
        if (indexOfClose != -1 && indexOfOpen!= -1) {
            // parse pasv response into array containing the ip and port number componenents
            String[] ipAndPortNumbers = pasvResponse.substring(indexOfOpen + 1, indexOfClose).split(",");
            String hostIP = "";
            // host ip is the first 4 8 bit numbers in the array defined above
            for (int i = 0; i < 4; i++) {
                hostIP += ipAndPortNumbers[i];
                if (i != 3) {
                    hostIP += ".";
                }
            }
            int portNumber1 = Integer.parseInt(ipAndPortNumbers[4]) * 256;
            int portNumber2 = Integer.parseInt(ipAndPortNumbers[5]);
            int portNumber = portNumber1 + portNumber2;

            //setup data socket based on IP and port number retrieved
            try {
                dataSocket = new Socket(hostIP, portNumber);
                dataInStream = dataSocket.getInputStream();
                dataIn = new BufferedReader(new InputStreamReader(dataInStream));
            } catch (UnknownHostException e) {
                System.err.println("0x3A2 Data transfer connection to " + hostIP + "on port " + portNumber + " failed to open");
            } catch (IOException e) {
                System.err.println("0x3A7 Data transfer connection I/O error, closing data connection.");
            }

        } else {
            // display error
            System.err.println("0xFFFF Processing error. Unable to read response.");
        }
    }

    /**
     * This method lists the features or extentions available on the server
     *
     * @param userInput the command features
     */
    public static void handleFeatures(String[] userInput){
        if (userInput.length != 1) {
            inputError();
            return;
        }
        System.out.println(REQUEST_PREFIX + "features");
        out.println("FEAT");
        try {
            String response;
            while ((response = in.readLine()) != null) {
                System.out.println(RESPONSE_PREFIX + response);
                if (response.contains("End")) {
                    return;
                }
            }
        } catch (IOException e) {
            System.err.println("0xFFFF Processing error. Unable to read response.");
        }
    }

    /**
     * This method changes the working directory that is being accessed.
     *
     * @param userInput the command "cd" and the directory that the user wishes to access
     */
    public static void handleCd(String[] userInput){
        if (userInput.length != 2) {
            inputError();
            return;
        }
        System.out.println(REQUEST_PREFIX + userInput[0] + " " + userInput[1]);
        out.println("CWD " + userInput[1]);
        try {
            System.out.println(RESPONSE_PREFIX + in.readLine());
        } catch (IOException e) {
            System.err.println("0xFFFF Processing error. Unable to read response.");
        }
    }

    /**
     * This method lists the files and drives of the directory that the user is in.
     *
     * @param userInput the command "dir"
     */
    public static void handleDir(String[] userInput){
        if (userInput.length != 1) {
            inputError();
            return;
        }
        establishDataConnection();
        System.out.println(REQUEST_PREFIX + "LIST ");
        out.println("LIST ");
        try {
            System.out.println(RESPONSE_PREFIX + in.readLine());
            System.out.println(RESPONSE_PREFIX + in.readLine());
            String response;
            while ((response = dataIn.readLine()) != null) {
                System.out.println(RESPONSE_PREFIX + response);
            }
            dataSocket.close();
        } catch (IOException e) {
            System.err.println("0xFFFF Processing error. Unable to read response.");
        }
    }

    /**
     * This method checks receives a socket that the user wants to be closed, then
     * checks if it's already closed or is null before closing.
     */
    public static void closeSocket() {
        if (socket.isClosed() || socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException e) {
            System.err.println("0xFFFF Processing Error. Failed to close the socket");
        }
    }

    /**
     * This method simply handles the error where an
     * incorrect number of arguments is provided.
     */
    private static void inputError() {
        System.err.println("0x002 Incorrect number of arguments.");
    }

}
