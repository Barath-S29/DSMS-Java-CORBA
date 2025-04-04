package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class UDPServerThread extends Thread {
    private final int udpPort;
    private final ShareMarketServerImpl serverImpl;
    private final Map<String, Map<String, Share>> shareDatabase;

    public UDPServerThread(int udpPort, ShareMarketServerImpl serverImpl, Map<String, Map<String, Share>> shareDatabase) {
        this.udpPort = udpPort;
        this.serverImpl = serverImpl;
        this.shareDatabase = shareDatabase;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(udpPort)) {
            System.out.println("UDP Server is running on port " + udpPort);
            byte[] buffer = new byte[4096];

            while (true) {
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                socket.receive(request);

                String receivedMessage = new String(request.getData(), 0, request.getLength());
                InetAddress clientAddress = request.getAddress();
                int clientPort = request.getPort();

                // Process request and generate response
                String responseMessage = processUDPRequest(receivedMessage);

                // Send response back
                byte[] responseBytes = responseMessage.getBytes();
                DatagramPacket response = new DatagramPacket(responseBytes, responseBytes.length,
                        clientAddress, clientPort);
                socket.send(response);
            }
        } catch (IOException e) {
            System.err.println("UDP Server Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String processUDPRequest(String request) {
        String[] parts = request.split(" ");
        String command = parts[0];

        switch (command) {
            case "LIST_AVAILABILITY":
                if (parts.length < 2) return "INVALID_REQUEST_FORMAT";
                String shareType = parts[1];
                return getLocalShareAvailability(shareType);

            case "CHECK_SWAP_AVAILABILITY":
                if (parts.length < 4) return "INVALID_REQUEST_FORMAT";
                String shareID = parts[1];
                shareType = parts[2];
                int requiredCount = Integer.parseInt(parts[3]);
                return checkSwapAvailability(shareID, shareType, requiredCount);

            case "EXECUTE_SWAP":
                if (parts.length < 7) return "INVALID_REQUEST_FORMAT";
                String buyerID = parts[1];
                String oldShareID = parts[2];
                String oldShareType = parts[3];
                String newShareID = parts[4];
                String newShareType = parts[5];
                int shareCount = Integer.parseInt(parts[6]);
                return executeSwap(buyerID, oldShareID, oldShareType, newShareID, newShareType, shareCount);

            default:
                return "INVALID_REQUEST";
        }
    }

    private String getLocalShareAvailability(String shareType) {
        StringBuilder result = new StringBuilder();
        synchronized (shareDatabase) {
            if (shareDatabase.containsKey(shareType)) {
                for (Share share : shareDatabase.get(shareType).values()) {
                    result.append("Share: ").append(share.getShareID())
                            .append(", Type: ").append(share.getShareType())
                            .append(", Available: ").append(share.getAvailableCapacity())
                            .append("\n");
                }
            }
        }
        return result.toString().trim();
    }

    private String checkSwapAvailability(String shareID, String shareType, int requiredCount) {
        synchronized (shareDatabase) {
            if (!shareDatabase.containsKey(shareType) ||
                    !shareDatabase.get(shareType).containsKey(shareID)) {
                return "NOT_AVAILABLE:Share not found";
            }

            Share share = shareDatabase.get(shareType).get(shareID);
            if (share.getAvailableCapacity() < requiredCount) {
                return "NOT_AVAILABLE:Not enough shares available. Required: " + requiredCount +
                        ", Available: " + share.getAvailableCapacity();
            }

            return "AVAILABLE:Share available for swap";
        }
    }

    private String executeSwap(String buyerID, String oldShareID, String oldShareType,
                               String newShareID, String newShareType, int shareCount) {
        synchronized (shareDatabase) {
            try {
                // Double-check availability
                if (!shareDatabase.containsKey(newShareType) ||
                        !shareDatabase.get(newShareType).containsKey(newShareID)) {
                    return "FAILED:New share not found";
                }

                Share newShare = shareDatabase.get(newShareType).get(newShareID);
                if (newShare.getAvailableCapacity() < shareCount) {
                    return "FAILED:Not enough new shares available";
                }

                // Reduce capacity of new share
                newShare.reduceCapacity(shareCount);

                // Add share to buyer's holdings
                serverImpl.getShareDatabase().get(newShareType).get(newShareID).addBuyer(buyerID);

                // Add to buyer holdings
                Map<String, Integer> buyerHoldings = serverImpl.getBuyerHoldings().get(buyerID);
                if (buyerHoldings == null) {
                    buyerHoldings = new HashMap<>();
                    serverImpl.getBuyerHoldings().put(buyerID, buyerHoldings);
                }

                String uniqueNewKey = newShareType + "-" + newShareID;
                buyerHoldings.put(uniqueNewKey, buyerHoldings.getOrDefault(uniqueNewKey, 0) + shareCount);

                System.out.println("Swap executed: Buyer " + buyerID + " acquired " + shareCount +
                        " shares of " + uniqueNewKey);

                return "SUCCESS:Swapped " + shareCount + " shares of " + newShareType + "-" + newShareID;
            } catch (Exception e) {
                System.err.println("Error executing swap: " + e.getMessage());
                e.printStackTrace();
                return "FAILED:Internal server error";
            }
        }
    }
}