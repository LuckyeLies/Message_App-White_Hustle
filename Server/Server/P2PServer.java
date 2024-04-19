package Server;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import Common.UserData;

public class P2PServer {
    private static final int PORT = 12345;
    private static final String DB_URL = "jdbc:mariadb://localhost:3306/white_shuttle";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root";
    private static Connection conn = null;
    private static final Map<String, UserData> onlineUsers = new HashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) {
        try {
            // Establish connection to the database
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("Connected to database");

            // Create a table in the database to store the user data
            createUsersTable();

            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("Server started on port " + PORT);

                // Start a thread to periodically check for inactive users
                scheduler.scheduleAtFixedRate(() -> checkInactiveUsers(), 1, 10, TimeUnit.SECONDS);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new ClientHandler(clientSocket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void createUsersTable() {
        try (Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS Users " +
                         "(publicKey VARCHAR(255) PRIMARY KEY, " +
                         "ipAddress VARCHAR(255), " +
                         "username VARCHAR(255))";
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    

    private static void checkInactiveUsers() {
        long currentTime = System.currentTimeMillis();
        /*
        try {
            long currentTime = System.currentTimeMillis();

            long inactiveThreshold = currentTime - (10 * 1000); // 10 seconds

            try (PreparedStatement pstmt = conn.prepareStatement(
                "DELETE FROM Users WHERE lastPingTime < ?")) {
                pstmt.setLong(1, inactiveThreshold);
                int deletedRows = pstmt.executeUpdate();
                if(deletedRows > 0) {
                    System.out.println(deletedRows + " user(s) have been removed due to inactivity.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    */
    /*
        long inactiveThreshold = currentTime - TimeUnit.SECONDS.toMillis(10);

        try (PreparedStatement pstmt = conn.prepareStatement(
                "DELETE FROM Users WHERE lastPingTime < ?")) {
            pstmt.setLong(1, inactiveThreshold);
            int deletedRows = pstmt.executeUpdate();
            if (deletedRows > 0) {
                System.out.println(deletedRows + " user(s) have been removed due to inactivity.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        )
        }
        */
        List<String> usersToRemove = new ArrayList<>();

        for (Map.Entry<String, UserData> entry : onlineUsers.entrySet()) {
            long lastPingTime = entry.getValue().getLastPingTime();
            if (currentTime - lastPingTime > TimeUnit.SECONDS.toMillis(10)) {
                usersToRemove.add(entry.getKey());
            }
        }

        for (String publicKey : usersToRemove) {
            onlineUsers.remove(publicKey);
            System.out.println("User with public key '" + publicKey + "' has been removed due to inactivity.");
            deleteUserFromDatabase(publicKey);
        }
    }

    private static void deleteUserFromDatabase(String publicKey) {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "DELETE FROM Users WHERE publicKey = ?")) {
            pstmt.setString(1, publicKey);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
                 ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream())) {

                // Receive user information as serialized object
                UserData userData = (UserData) inputStream.readObject();
                onlineUsers.put(userData.getPublicKey(), userData);

                insertOrUpdateUser(userData);
                
                System.out.println("User with public key '" + userData.getPublicKey() + "' is online.");

                while (true) {
                    // Receive ping as serialized object
                    UserData pingData = (UserData) inputStream.readObject();

                    // Update the last ping time in the database
                    updatePingTime(pingData.getPublicKey());

                    onlineUsers.get(pingData.getPublicKey()).setLastPingTime(System.currentTimeMillis());
                    //List<UserData> onlineUserList = new ArrayList<>(onlineUsers.values());

                    List<UserData> onlineUserList = getOnlineUserList();
                    outputStream.writeObject(onlineUserList);
                }
            } catch (IOException | ClassNotFoundException | SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private static void insertOrUpdateUser(UserData userdata) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "INSERT INTO Users (publicKey, ipAddress, username) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE ipAddress = VALUES(ipAddress), username = VALUES(username)")) {
            pstmt.setString(1, userdata.getPublicKey());
            pstmt.setString(2, userdata.getIpAddress());
            pstmt.setString(3, userdata.getUsername());
            pstmt.executeUpdate();
        }
    }

    private static void updatePingTime(String publicKey) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "UPDATE Users SET lastPingTime = ? WHERE publicKey = ?")) {
            pstmt.setLong(1, System.currentTimeMillis());
            pstmt.setString(2, publicKey);
            pstmt.executeUpdate();
        }
    }

    private static List<UserData> getOnlineUserList() throws SQLException {
        List<UserData> onlineUserList = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM Users")) {

            while (rs.next()) {
                String publicKey = rs.getString("publicKey");
                String ipAddress = rs.getString("ipAddress");
                String username = rs.getString("username");
                onlineUserList.add(new UserData(publicKey, ipAddress, username));
            }
        }
        return onlineUserList;
    }
}
