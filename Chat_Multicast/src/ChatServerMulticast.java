import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServerMulticast {
    private static final int TCP_PORT = 1235;
    private static final String MULTICAST_ADDRESS = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;

    private static Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static Map<String, Set<String>> groups = new ConcurrentHashMap<>();
    private static MulticastSocket multicastSocket;
    private static InetAddress multicastGroup;


    public static void main(String[] args) {
        try {
            multicastSocket = new MulticastSocket(MULTICAST_PORT);
            multicastGroup = InetAddress.getByName(MULTICAST_ADDRESS);
            multicastSocket.joinGroup(multicastGroup);
            System.out.println("Multicast group joined: " + MULTICAST_ADDRESS + ":" + MULTICAST_PORT);

            groups.put("General", new HashSet<>());

            try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
                System.out.println("Chat Server started on TCP port " + TCP_PORT);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket);
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    new Thread(clientHandler).start();
                }
            }
        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void broadcastToAll(String message, ClientHandler sender) {
    	String formattedMsg = String.format("[%s (%s)] %s", 
    	    sender.getClientIpAddress(), sender.getClientName(), message);
        try {
            byte[] buffer = formattedMsg.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, multicastGroup, MULTICAST_PORT);
            multicastSocket.send(packet);
            System.out.println("Broadcast via multicast: " + formattedMsg);
        } catch (IOException e) {
            System.err.println("Error broadcasting: " + e.getMessage());
        }
    }
    

    public static void sendToGroup(String groupName, String message, ClientHandler sender) {
        Set<String> members = groups.get(groupName);
        if (members != null) {
            // Định dạng tin nhắn có đầy đủ thông tin IP và tên người gửi
            String formattedMsg = String.format("[Group: %s from %s (%s)] %s",
                    groupName, sender.getClientIpAddress(), sender.getClientName(), message);
            
            for (String memberName : members) {
                ClientHandler client = clients.get(memberName);
                
                // ĐIỀU KIỆN GÂY LỖI "!memberName.equals(sender)" ĐÃ BỊ XÓA
                // Giờ đây server sẽ gửi tin nhắn cho TẤT CẢ thành viên, bao gồm cả người gửi.
                if (client != null) {
                    client.sendMessage(formattedMsg); // Gửi qua TCP đến từng người
                }
            }
        } else {
            // Thêm thông báo lỗi nếu nhóm không tồn tại
            sender.sendMessage("[System] Group '" + groupName + "' not found.");
        }
    }

    public static void sendPrivateMessage(ClientHandler sender, String toUser, String message) {
        ClientHandler recipient = clients.get(toUser);
        String formattedMsg = String.format("[Private from %s (%s)] %s",
                sender.getClientIpAddress(), sender.getClientName(), message);
        
        if (recipient != null && recipient.isConnected()) {
            recipient.sendMessage(formattedMsg);
            sender.sendMessage(String.format("[Private to %s] %s", toUser, message));
        } else {
            sender.sendMessage("[System] User '" + toUser + "' not found or offline.");
        }
    }

    public static void sendPrivateMessageByIp(ClientHandler sender, String targetIp, String message) {
        ClientHandler recipient = null;
        for (ClientHandler handler : clients.values()) {
            if (handler.getClientIpAddress().equals(targetIp)) {
                recipient = handler;
                break;
            }
        }
        String formattedMsg = String.format("[Private from %s (%s)] %s",
                sender.getClientIpAddress(), sender.getClientName(), message);
        if (recipient != null && recipient.isConnected()) {
            recipient.sendMessage(formattedMsg);
            sender.sendMessage(String.format("[Private to %s (%s)] %s",
                    recipient.getClientName(), targetIp, message));
        } else {
            sender.sendMessage("[System] IP '" + targetIp + "' not found or offline.");
        }
    }
    
    public static void createGroup(String groupName, ClientHandler creator) {
        if (!groups.containsKey(groupName)) {
            Set<String> members = new HashSet<>();
            members.add(creator.getClientName());
            groups.put(groupName, members);
            creator.sendMessage("[System] Group '" + groupName + "' created successfully.");
        } else {
             creator.sendMessage("[System] Group '" + groupName + "' already exists.");
        }
    }

    public static void joinGroup(String groupName, ClientHandler user) {
        Set<String> members = groups.get(groupName);
        if (members != null) {
            members.add(user.getClientName());
            user.sendMessage("[System] You joined group '" + groupName + "'.");
            sendToGroup(groupName, "has joined the group.", user);
        } else {
            user.sendMessage("[System] Group '" + groupName + "' not found.");
        }
    }
    
    public static void leaveGroup(String groupName, ClientHandler user) {
        Set<String> members = groups.get(groupName);
        if (members != null && members.contains(user.getClientName())) {
            sendToGroup(groupName, "has left the group.", user);
            members.remove(user.getClientName());
            user.sendMessage("[System] You have left group '" + groupName + "'.");
        } else {
             user.sendMessage("[System] You are not a member of group '" + groupName + "'.");
        }
    }

    public static String getOnlineUsers() {
        StringBuilder userList = new StringBuilder("[System] Online users:\n");
        clients.values().forEach(c -> userList.append(String.format("- %s (%s)\n", c.getClientName(), c.getClientIpAddress())));
        return userList.toString();
    }

    public static String getGroupList() {
        return "[System] Available groups: " + String.join(", ", groups.keySet());
    }

    public static void addClient(String name, ClientHandler handler) {
        clients.put(name, handler);
        joinGroup("General", handler);
    }

    public static void removeClient(String name, ClientHandler handler) {
        if (name == null) return;
        clients.remove(name);
        for (Set<String> members : groups.values()) {
            members.remove(name);
        }
        System.out.println("Client disconnected: " + name);
        broadcastToAll(name + " has left the chat.", handler);
    }

    public static boolean isIpAddress(String text) {
        if (text == null) {
            return false;
        }
        String[] parts = text.split("\\.");

        if (parts.length != 4) {
            return false;
        }

        for (String part : parts) {
            try {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false; // Số nằm ngoài khoảng cho phép
                }
            } catch (NumberFormatException e) {
                return false; // Nếu không chuyển thành số được (ví dụ: "abc")
            }
        }

        // 4. Nếu mọi thứ đều ổn
        return true;
    }
}

class ClientHandler implements Runnable {
    private Socket clientSocket;
    private DataInputStream dataIn;
    private DataOutputStream dataOut;
    private String clientName;
    private String clientIpAddress;
    private boolean connected = true;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.clientIpAddress = socket.getInetAddress().getHostAddress();
        try {
            dataIn = new DataInputStream(clientSocket.getInputStream());
            dataOut = new DataOutputStream(clientSocket.getOutputStream());
        } catch (IOException e) {
            System.err.println("Error setting up streams: " + e.getMessage());
            connected = false;
        }
    }

    public String getClientName() { return clientName; }
    public String getClientIpAddress() { return clientIpAddress; }

    public boolean isConnected() {
        return connected && !clientSocket.isClosed();
    }

    public void sendMessage(String message) {
        if (!isConnected()) return;
        try {
            dataOut.writeUTF("MESSAGE");
            dataOut.writeUTF(message);
            dataOut.flush();
        } catch (IOException e) {
            System.err.println("Error sending message to " + clientName + ": " + e.getMessage());
            disconnect();
        }
    }

    private void disconnect() {
        if (!connected) return;
        connected = false;
        try {
            if (dataIn != null) dataIn.close();
            if (dataOut != null) dataOut.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            System.err.println("Error closing resources: " + e.getMessage());
        }
        ChatServerMulticast.removeClient(clientName, this);
    }

    @Override
    public void run() {
        try {
            clientName = dataIn.readUTF();
            System.out.println(clientName + " (" + clientIpAddress + ") has joined the chat.");
            ChatServerMulticast.addClient(clientName, this);
            ChatServerMulticast.broadcastToAll(clientName + " has joined the chat.", this);
            
            while (connected && !clientSocket.isClosed()) {
                String messageType = dataIn.readUTF();
                if ("MESSAGE".equals(messageType)) {
                    String message = dataIn.readUTF();
                    handleMessage(message);
                }
            }
        } catch (IOException e) {
            System.err.println(clientName + " disconnected: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void handleMessage(String message) {
        System.out.println(String.format("Received from %s (%s): %s", clientName, clientIpAddress, message));
        
        if (message.startsWith("net send ")) {
            String[] parts = message.substring(9).split(" ", 2);
            if (parts.length >= 2) {
                String target = parts[0];
                String msgContent = parts[1];

                if ("*".equals(target)) {
                    ChatServerMulticast.broadcastToAll(msgContent, this);
                } else if (ChatServerMulticast.isIpAddress(target)) { // Vẫn gọi hàm này nhưng giờ nó đã dễ hiểu
                    ChatServerMulticast.sendPrivateMessageByIp(this, target, msgContent);
                } else {
                    ChatServerMulticast.sendToGroup(target, msgContent, this);
                }
            } else {
                sendMessage("[System] Invalid 'net send' command format. Use: net send {IP|group|*} message");
            }
        } 
        else if (message.startsWith("/private ")) {
            String[] parts = message.substring(9).split(" ", 2);
            if (parts.length >= 2) {
                ChatServerMulticast.sendPrivateMessage(this, parts[0], parts[1]);
            }
        } else if (message.startsWith("/group ")) {
            String[] parts = message.substring(7).split(" ", 2);
            if (parts.length >= 2) {
                ChatServerMulticast.sendToGroup(parts[0], clientName + ": " + parts[1], this);
            }
        } else if (message.startsWith("/create ")) {
            String groupName = message.substring(8).trim();
            ChatServerMulticast.createGroup(groupName, this);
        } else if (message.startsWith("/join ")) {
            String groupName = message.substring(6).trim();
            ChatServerMulticast.joinGroup(groupName, this);
        } else if (message.startsWith("/leave ")) {
            String groupName = message.substring(7).trim();
            ChatServerMulticast.leaveGroup(groupName, this);
        } else if (message.equals("/users")) {
            sendMessage(ChatServerMulticast.getOnlineUsers());
        } else if (message.equals("/groups")) {
            sendMessage(ChatServerMulticast.getGroupList());
        } else {
            ChatServerMulticast.broadcastToAll(message, this);
        }
    }
}
