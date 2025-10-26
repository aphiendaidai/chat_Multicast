import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.net.NetworkInterface;
public class ChatClientMulticast extends JFrame {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 1235;
    private static final String MULTICAST_ADDRESS = "230.0.0.1";	
    private static final int MULTICAST_PORT = 4446;

    private JPanel messagePanel;
    private JScrollPane scrollPane;
    private JTextField messageInput;
    private DataOutputStream dataOut;
    private String clientName;

    public ChatClientMulticast() {
        super("Chat Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 700);
        setLocationRelativeTo(null);
        
        clientName = JOptionPane.showInputDialog(this, "Enter your name:", "Client Name", JOptionPane.PLAIN_MESSAGE);
        if (clientName == null || clientName.trim().isEmpty()) {
            clientName = "Guest" + new Random().nextInt(1000);
        }
        setTitle("Chat Client - " + clientName);
        
        setupUI();
        connectToServer();
        startMulticastReceiver();
    }

    private void setupUI() {
        // --- GIAO DIỆN CHÍNH ---
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(245, 245, 245));

        // KHU VỰC HIỂN THỊ TIN NHẮN
        messagePanel = new JPanel();
        messagePanel.setLayout(new GridBagLayout());
        messagePanel.setBackground(Color.WHITE);

        scrollPane = new JScrollPane(messagePanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16); // Cuộn mượt hơn

        // KHU VỰC NHẬP LIỆU
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        bottomPanel.setBackground(new Color(245, 245, 245));

        messageInput = new JTextField();
        messageInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messageInput.setBorder(new RoundedBorder(15, new Color(220, 220, 220)));
        messageInput.addActionListener(e -> sendMessage());
        
        JButton sendButton = new JButton("Send");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        
        // Nút quản lý
        JButton manageButton = new JButton("...");
        manageButton.setFont(new Font("Segoe UI", Font.BOLD, 14));

        bottomPanel.add(messageInput, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        bottomPanel.add(manageButton, BorderLayout.WEST);

        // Gắn các thành phần vào frame
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        // Action Listeners
        sendButton.addActionListener(e -> sendMessage());
        manageButton.addActionListener(e -> showManageDialog());

        setVisible(true);
    }
 // TÌM HÀM NÀY TRONG FILE ChatClientMulticast.java VÀ THAY THẾ NÓ

    private void addMessage(String rawMessage) {
        SwingUtilities.invokeLater(() -> {
            boolean isMyMessage = rawMessage.startsWith("[Private to") || rawMessage.contains("(" + clientName + ")]");
            boolean isSystemMessage = rawMessage.startsWith("[System]") || rawMessage.startsWith("[Error]");

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 10, 5, 10);
            gbc.weightx = 1.0;
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            
            // --- TẠO BONG BÓNG CHAT ---
            JTextArea textArea = new JTextArea();
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            
            JPanel bubble = new JPanel(new BorderLayout());
            bubble.setBorder(new RoundedBorder(15, Color.LIGHT_GRAY));

            // PHÂN TÍCH VÀ ĐỊNH DẠNG TIN NHẮN
            String senderInfo = "";
            String messageContent = rawMessage;

            if (!isSystemMessage && rawMessage.contains("] ")) {
                senderInfo = rawMessage.substring(1, rawMessage.indexOf("] "));
                messageContent = rawMessage.substring(rawMessage.indexOf("] ") + 2);
            }
            textArea.setText(messageContent);
            
            if (!senderInfo.isEmpty() && !isMyMessage) {
                JLabel senderLabel = new JLabel(senderInfo);
                senderLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
                senderLabel.setForeground(Color.DARK_GRAY);
                senderLabel.setBorder(new EmptyBorder(5, 10, 0, 10));
                bubble.add(senderLabel, BorderLayout.NORTH);
            }
            
            JPanel textWrapper = new JPanel(new BorderLayout());
            textWrapper.setBorder(new EmptyBorder(5, 10, 5, 10));
            textWrapper.add(textArea, BorderLayout.CENTER);

            bubble.add(textWrapper, BorderLayout.CENTER);

            // --- THIẾT LẬP MÀU SẮC VÀ CĂN LỀ ---
            if (isMyMessage) {
                gbc.anchor = GridBagConstraints.LINE_END; // Căn phải
                bubble.setBackground(new Color(0, 132, 255));
                textArea.setForeground(Color.WHITE);
            } else if (isSystemMessage) {
                gbc.anchor = GridBagConstraints.CENTER; // Căn giữa
                bubble.setBackground(new Color(215, 235, 255));
                textArea.setForeground(Color.DARK_GRAY);
            } else {
                gbc.anchor = GridBagConstraints.LINE_START; // Căn trái
                bubble.setBackground(new Color(230, 230, 230));
                textArea.setForeground(Color.BLACK);
            }
            textArea.setBackground(bubble.getBackground());
            textWrapper.setBackground(bubble.getBackground());

            messagePanel.add(bubble, gbc);

            // Cập nhật lại giao diện để nó tính toán kích thước mới
            messagePanel.revalidate();
            messagePanel.repaint();

            // **ĐÂY LÀ PHẦN SỬA LỖI QUAN TRỌNG NHẤT**
            // Gói lệnh cuộn trong invokeLater để đảm bảo nó được chạy
            // SAU KHI giao diện đã được cập nhật hoàn toàn.
            SwingUtilities.invokeLater(() -> {
                JScrollBar vertical = scrollPane.getVerticalScrollBar();
                vertical.setValue(vertical.getMaximum());
            });
        });
    }
    private void sendMessage() {
        String message = messageInput.getText();
        if (!message.trim().isEmpty()) {
            try {
                dataOut.writeUTF("MESSAGE");
                dataOut.writeUTF(message);
                dataOut.flush();
                messageInput.setText("");
            } catch (IOException e) {
                addMessage("[Error] Failed to send message: " + e.getMessage());
            }
        }
    }

    private void showManageDialog() {
        String[] options = {"List Users", "List Groups", "Create Group", "Join Group", "Leave Group", "Private Msg"};
        int choice = JOptionPane.showOptionDialog(this, "Select an action:", "Management",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        
        try {
            switch (choice) {
                case 0: dataOut.writeUTF("MESSAGE"); dataOut.writeUTF("/users"); dataOut.flush(); break;
                case 1: dataOut.writeUTF("MESSAGE"); dataOut.writeUTF("/groups"); dataOut.flush(); break;
                case 2: sendCommandWithInput("/create", "Enter new group name:"); break;
                case 3: sendCommandWithInput("/join", "Enter group name to join:"); break;
                case 4: sendCommandWithInput("/leave", "Enter group name to leave:"); break;
                case 5: sendPrivateMessage(); break;
            }
        } catch (IOException e) {
            addMessage("[Error] Failed to execute command: " + e.getMessage());
        }
    }
    
    private void sendCommandWithInput(String command, String prompt) throws IOException {
        String input = JOptionPane.showInputDialog(this, prompt);
        if (input != null && !input.trim().isEmpty()) {
            dataOut.writeUTF("MESSAGE");
            dataOut.writeUTF(command + " " + input);
            dataOut.flush();
        }
    }

    private void sendPrivateMessage() {
        try {
            String recipient = JOptionPane.showInputDialog(this, "Enter recipient name:");
            if (recipient != null && !recipient.trim().isEmpty()) {
                String message = JOptionPane.showInputDialog(this, "Enter private message to " + recipient + ":");
                if (message != null) {
                    dataOut.writeUTF("MESSAGE");
                    dataOut.writeUTF("/private " + recipient + " " + message);
                    dataOut.flush();
                }
            }
        } catch (IOException e) {
            addMessage("[Error] Failed to send private message: " + e.getMessage());
        }
    }

    private void connectToServer() {
        try {
            Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            DataInputStream dataIn = new DataInputStream(socket.getInputStream());
            dataOut = new DataOutputStream(socket.getOutputStream());
            dataOut.writeUTF(clientName);
            dataOut.flush();
            
            new Thread(() -> {
                try {
                    while (true) {
                        String messageType = dataIn.readUTF();
                        if ("MESSAGE".equals(messageType)) {
                            String message = dataIn.readUTF();
                            addMessage(message);
                        }
                    }
                } catch (IOException e) {
                    addMessage("[System] Disconnected from server.");
                }
            }).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not connect to server.", "Connection Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
    
    private void startMulticastReceiver() {
        new Thread(() -> {
            try (MulticastSocket multicastSocket = new MulticastSocket(MULTICAST_PORT)){
                InetAddress multicastGroup = InetAddress.getByName(MULTICAST_ADDRESS);
                NetworkInterface loopbackInterface = NetworkInterface.getByInetAddress(InetAddress.getLoopbackAddress());
            if (loopbackInterface != null) {
                clientMulticastSocket.setNetworkInterface(loopbackInterface);
            }
                multicastSocket.joinGroup(multicastGroup);
                byte[] buffer = new byte[4096];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    multicastSocket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    addMessage(message);
                }
            } catch (IOException e) {
                System.err.println("Multicast error: " + e.getMessage());
                addMessage("[Error] Multicast receiver failed.");
            }
        }).start();
    }
    
    public static void main(String[] args) {
        // Đặt Look and Feel của hệ thống để giao diện đẹp hơn
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(ChatClientMulticast::new);
    }
}

