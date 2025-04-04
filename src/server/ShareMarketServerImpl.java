package server;

import ShareMarket.*;
import org.omg.CORBA.*;
import org.omg.CosNaming.*;
import org.omg.CosNaming.NamingContextPackage.*;
import org.omg.PortableServer.*;
//import org.omg.PortableServer.POA;

import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ShareMarketServerImpl extends ShareMarket.ServerPOA {

    private final String city;
    private final int udpPort;
    private static final int DEFAULT_PORT = 5000;
    private final Map<String, Map<String, Share>> shareDatabase = new HashMap<>();
    private final Map<String, Integer> remoteServers = new HashMap<>();
    private final Map<String, Map<String, Integer>> buyerHoldings = new HashMap<>();

    // CORBA ORB reference
    public static ORB orb;

    public ShareMarketServerImpl(String city, int udpPort) {
        this.city = city;
        this.udpPort = udpPort;
        initializeShareTypes();
    }

    public static void setORB(ORB orb_val) {
        orb = orb_val;
    }

    private void initializeShareTypes() {
        shareDatabase.put("Equity", new HashMap<>());
        shareDatabase.put("Bonus", new HashMap<>());
        shareDatabase.put("Dividend", new HashMap<>());
    }

    public void addRemoteServer(String city, int port) {
        remoteServers.put(city, port);
    }

    public Map<String, Map<String, Share>> getShareDatabase() {
        return this.shareDatabase;
    }

    public Map<String, Map<String, Integer>> getBuyerHoldings() {
        return this.buyerHoldings;
    }

    private void logAction(String requestType, String requestParams, boolean success) {
        try {
            FileWriter writer = new FileWriter("logs/" + city + "_Server.log", true);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            writer.write("[" + timestamp + "] " + requestType + " | Params: " + requestParams + " | Status: " + (success ? "Successfully Completed" : "Failed") + "\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public synchronized String addShare(String shareID, String shareType, int availableCapacity) {
        shareDatabase.putIfAbsent(shareType, new HashMap<>());

        if (shareDatabase.get(shareType).containsKey(shareID)) {
            logAction("Add Share", "ShareID: " + shareID + ", ShareType: " + shareType, false);
            return "Share already exists with ID " + shareID + " and Type " + shareType;
        }

        if(shareType.equalsIgnoreCase("equity") || shareType.equalsIgnoreCase("bonus") || shareType.equalsIgnoreCase("dividend")){
            shareDatabase.get(shareType).put(shareID, new Share(shareID, shareType, availableCapacity));
            logAction("Add Share", "ShareID: " + shareID + ", ShareType: " + shareType + ", Capacity: " + availableCapacity, true);
            return "Share added successfully: " + shareType + "-" + shareID;
        }
        logAction("Add Share", "ShareID: " + shareID + " , ShareType: "+ shareType, false);
        return "Share not added: "+shareType + "-" +shareID;
    }

    @Override
    public synchronized String getShares(String buyerID) {
        // Strip depth marker if present for cleaner processing
        String cleanBuyerID = buyerID.contains("::DEPTH::") ?
                buyerID.split("::DEPTH::")[0] : buyerID;

        StringBuilder result = new StringBuilder("Your Shares:\n");
        boolean hasShares = false;

        // Check for local shares
        if (buyerHoldings.containsKey(cleanBuyerID) && !buyerHoldings.get(cleanBuyerID).isEmpty()) {
            result.append(this.city).append(" Market Shares:\n");
            for (Map.Entry<String, Integer> entry : buyerHoldings.get(cleanBuyerID).entrySet()) {
                result.append("[Share: ").append(entry.getKey())
                        .append(", Owned: ").append(entry.getValue()).append("]\n");
            }
            hasShares = true;
        }

        // Only check remote markets if this isn't already a recursive call
        if (!buyerID.contains("::DEPTH::")) {
            // Add depth marker to prevent infinite recursion
            String depthMarkedID = cleanBuyerID + "::DEPTH::1";

            // Try to get shares from each remote market
            for (String remoteName : remoteServers.keySet()) {
                if (!remoteName.equalsIgnoreCase(this.city)) {
                    try {
                        // Get CORBA reference for remote server
                        ShareMarket.Server remoteServer = getRemoteServerRef(remoteName+"ShareMarketServer");
                        if (remoteServer == null) {
                            System.out.println("Failed to get reference for market: " + remoteName);
                            continue;
                        }

                        // Get shares from remote server
                        String remoteSharesResponse = remoteServer.getShares(depthMarkedID);
                        System.out.println("DEBUG - Remote response from " + remoteName + ": " + remoteSharesResponse);

                        // Only process if the buyer has shares in this remote market
                        if (!remoteSharesResponse.equals("You do not own any shares in any market.")) {
                            // Extract just the shares part, removing the header
                            String[] lines = remoteSharesResponse.split("\n");
                            StringBuilder remoteShares = new StringBuilder();

                            boolean foundShareLines = false;
                            for (int i = 1; i < lines.length; i++) { // Skip the header line
                                if (lines[i].trim().startsWith("[Share:")) {
                                    remoteShares.append(lines[i]).append("\n");
                                    foundShareLines = true;
                                }
                            }

                            if (foundShareLines) {
                                if (hasShares) {
                                    result.append("\n"); // Add spacing between markets
                                }
                                result.append(remoteName).append(" Market Shares:\n");
                                result.append(remoteShares);
                                hasShares = true;
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Error fetching shares from " + remoteName + ": " + e.getMessage());
                        e.printStackTrace(); // More detailed error info will be shown in the server not in the client side
                    }
                }
            }
        }

        logAction("Get Shares", "BuyerID: " + cleanBuyerID, true);

        if (!hasShares) {
            return "You do not own any shares in any market.";
        }

        return result.toString();
    }

    @Override
    public synchronized String removeShare(String shareID, String shareType) {
        if (!shareDatabase.containsKey(shareType) || !shareDatabase.get(shareType).containsKey(shareID)) {
            logAction("Remove Share", "ShareID: " + shareID + ", ShareType: " + shareType, false);
            return "Share not found.";
        }

        shareDatabase.get(shareType).remove(shareID);
        logAction("Remove Share", "ShareID: " + shareID + ", ShareType: " + shareType, true);
        return "Share removed successfully.";
    }

    @Override
    public synchronized String purchaseShare(String buyerID, String shareID, String shareType, int shareCount) {
        String uniqueKey = shareType + "-" + shareID;

        if (shareDatabase.containsKey(shareType) && shareDatabase.get(shareType).containsKey(shareID)) {
            Share share = shareDatabase.get(shareType).get(shareID);
            if (share.getAvailableCapacity() < shareCount) {
                logAction("Purchase Share", "BuyerID: " + buyerID + ", ShareID: " + shareID + ", ShareType: " + shareType + ", Quantity: " + shareCount, false);
                return "Purchase failed. Not enough shares available.";
            }

            // Update the share's available capacity
            share.reduceCapacity(shareCount);

            // Update buyer's holdings
            buyerHoldings.putIfAbsent(buyerID, new HashMap<>());
            Map<String, Integer> holdings = buyerHoldings.get(buyerID);
            holdings.put(uniqueKey, holdings.getOrDefault(uniqueKey, 0) + shareCount);

            logAction("Purchase Share", "BuyerID: " + buyerID + ", ShareID: " + shareID + ", ShareType: " + shareType + ", Quantity: " + shareCount, true);
            return "Purchase successful. You bought " + shareCount + " of " + uniqueKey;
        }
        else {
            for (Map.Entry<String, Integer> entry : remoteServers.entrySet()) {
                String remoteName = entry.getKey();
                try {
                    return purchaseRemoteShare(buyerID, shareID, shareType, shareCount, remoteName);
                } catch (Exception e) {
                    // If this remote server doesn't have it, continue to the next one
                    continue;
                }
            }
            logAction("Purchase Share", "BuyerID: " + buyerID + ", ShareID: " + shareID, false);
            return "Purchase failed. Share not found in any market.";
        }
    }

    @Override
    public synchronized String purchaseRemoteShare(String buyerID, String shareID, String shareType,
                                                   int shareCount, String targetMarket) {
        try {
            // Get CORBA reference for remote server
            targetMarket+="ShareMarketServer";
//            System.out.println("The targetMarket in purchaseRemoteShare: "+targetMarket);
            ShareMarket.Server remoteServer = getRemoteServerRef(targetMarket);
            if (remoteServer == null) {
                logAction("Purchase Remote Share", "BuyerID: " + buyerID + ", ShareID: " + shareID +
                        ", Target: " + targetMarket, false);
                return "Purchase failed. Invalid target market.";
            }

            // Execute the purchase on the remote server
            String result = remoteServer.purchaseShare(buyerID, shareID, shareType, shareCount);

            // Log the cross-server transaction
            logAction("Purchase Remote Share", "BuyerID: " + buyerID + ", ShareID: " + shareID +
                    ", Target: " + targetMarket + ", Quantity: " + shareCount, true);

            return "Cross-server purchase: " + result;
        } catch (Exception e) {
            logAction("Purchase Remote Share", "BuyerID: " + buyerID + ", ShareID: " + shareID +
                    ", Target: " + targetMarket, false);
            return "Cross-server purchase failed: " + e.getMessage();
        }
    }

    @Override
    public synchronized String sellShare(String buyerID, String shareID, int quantity) {
        String uniqueKey = null;
        String shareType = null;

        if (buyerHoldings.containsKey(buyerID)) {
            for (String key : buyerHoldings.get(buyerID).keySet()) {
                if (key.endsWith("-" + shareID)) {
                    uniqueKey = key;
                    shareType = key.split("-")[0];
                    break;
                }
            }
        }

        if (uniqueKey != null) {
            int ownedShares = buyerHoldings.get(buyerID).get(uniqueKey);

            if (quantity > ownedShares) {
                logAction("Sell Share", "BuyerID: " + buyerID + ", ShareID: " + shareID + ", Quantity: " + quantity, false);
                return "Sell failed. You cannot sell more than you own.";
            }

            if (quantity == ownedShares) {
                buyerHoldings.get(buyerID).remove(uniqueKey);
            } else {
                buyerHoldings.get(buyerID).put(uniqueKey, ownedShares - quantity);
            }

            shareType = uniqueKey.split("-")[0];
            Share share = shareDatabase.get(shareType).get(shareID);
            share.increaseCapacity(quantity);

            logAction("Sell Share", "BuyerID: " + buyerID + ", ShareID: " + shareID + ", Quantity: " + quantity, true);
            return "Sell operation successful. Sold " + quantity + " of " + uniqueKey;
        }
        else {
            // If not found locally, find in other market
            for (Map.Entry<String, Integer> entry : remoteServers.entrySet()) {
                String remoteName = entry.getKey();
                try {
                    return sellRemoteShare(buyerID, shareID, null, quantity, remoteName);
                } catch (Exception e) {
                    continue;
                }
            }
            logAction("Sell Share", "BuyerID: " + buyerID + ", ShareID: " + shareID, false);
            return "Sell failed. Share not found in any market.";
        }
    }

    @Override
    public synchronized String sellRemoteShare(String buyerID, String shareID, String shareType, int shareCount, String targetMarket) {
        try {
            // Get CORBA reference for remote server
            String fullMarketName = targetMarket+"ShareMarketServer";
            ShareMarket.Server remoteServer = getRemoteServerRef(fullMarketName);
            if (remoteServer == null) {
                logAction("Sell Remote Share", "BuyerID: " + buyerID + ", ShareID: " + shareID +
                        ", Target: " + targetMarket, false);
                return "Sell failed. Invalid target market.";
            }

            // Execute the sell on the remote server
            String result = remoteServer.sellShare(buyerID, shareID, shareCount);

            // Log the cross-server transaction
            logAction("Sell Remote Share", "BuyerID: " + buyerID + ", ShareID: " + shareID +
                    ", Target: " + targetMarket + ", Quantity: " + shareCount, true);

            return "Cross-server sell: " + result;
        } catch (Exception e) {
            logAction("Sell Remote Share", "BuyerID: " + buyerID + ", ShareID: " + shareID +
                    ", Target: " + targetMarket, false);
            return "Cross-server sell failed: " + e.getMessage();
        }
    }

    @Override
    public synchronized String listShareAvailability(String shareType) {
        StringBuilder availability = new StringBuilder();

        synchronized (shareDatabase) {
            if (shareDatabase.containsKey(shareType)) {
                for (Share share : shareDatabase.get(shareType).values()) {
                    availability.append("[Share ID: ").append(share.getShareID())
                            .append(", Type: ").append(share.getShareType())
                            .append(", Available: ").append(share.getAvailableCapacity()).append("]\n");
                }
            }
        }

        for (Map.Entry<String, Integer> entry : remoteServers.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(this.city)) {
                int port = entry.getValue();
                String udpResponse = sendUDPRequest("localhost", port, "LIST_AVAILABILITY " + shareType);
                if (!udpResponse.isEmpty()) {
                    availability.append(udpResponse).append("\n");
                }
            }
        }
        return availability.toString().trim();
    }

    @Override
    public synchronized String swapShares(String buyerID, String oldShareID, String oldShareType,
                                          String newShareID, String newShareType) {
        // 1. Check if share types match
        if (!oldShareType.equals(newShareType)) {
            logAction("Swap Shares", "BuyerID: " + buyerID + ", OldShareID: " + oldShareID +
                    ", NewShareID: " + newShareID, false);
            return "Swap failed. Share types must be the same.";
        }

        // 2. Check if buyer owns the old share
        String uniqueOldKey = oldShareType + "-" + oldShareID;
        if (!buyerHoldings.containsKey(buyerID) ||
                !buyerHoldings.get(buyerID).containsKey(uniqueOldKey)) {
            logAction("Swap Shares", "BuyerID: " + buyerID + ", OldShareID: " + oldShareID, false);
            return "Swap failed. You do not own this share.";
        }

        // 3. Get quantity of old shares
        int ownedShareCount = buyerHoldings.get(buyerID).get(uniqueOldKey);

        // 4. Determine target market from new share ID (first letter)
        String targetCity = "";
        if (newShareID.startsWith("N")) {
            targetCity = "NewYork";
        } else if (newShareID.startsWith("L")) {
            targetCity = "London";
        } else if (newShareID.startsWith("T")) {
            targetCity = "Tokyo";
        } else {
            logAction("Swap Shares", "BuyerID: " + buyerID + ", NewShareID: " + newShareID, false);
            return "Swap failed. Invalid new share ID format.";
        }

        // 5. If target is the same as current market, handle locally
        System.out.println("Checking targetCity and current city"+this.city+" targetCity: "+targetCity);
        if (targetCity.equals(this.city)) {
            return handleLocalSwap(buyerID, oldShareID, oldShareType, newShareID, ownedShareCount);
        }
        // 6. Otherwise, perform cross-market swap using UDP
        return performCrossMarketSwap(buyerID, oldShareID, oldShareType, newShareID, newShareType,
                ownedShareCount, targetCity);
    }

    // Helper method for local swap
    private String handleLocalSwap(String buyerID, String oldShareID, String shareType,
                                   String newShareID, int shareCount) {
        String uniqueOldKey = shareType + "-" + oldShareID;
        String uniqueNewKey = shareType + "-" + newShareID;

        // Check if new share exists and has enough capacity
        if (!shareDatabase.containsKey(shareType) ||
                !shareDatabase.get(shareType).containsKey(newShareID)) {
            logAction("Local Swap", "BuyerID: " + buyerID + ", NewShareID: " + newShareID, false);
            return "Swap failed. New share not found.";
        }

        Share newShare = shareDatabase.get(shareType).get(newShareID);
        if (newShare.getAvailableCapacity() < shareCount) {
            logAction("Local Swap", "BuyerID: " + buyerID + ", NewShareID: " + newShareID, false);
            return "Swap failed. Not enough new shares available.";
        }

        // Perform the swap
        // 1. Return old shares to available pool
        Share oldShare = shareDatabase.get(shareType).get(oldShareID);
        oldShare.increaseCapacity(shareCount);

        // 2. Remove old shares from buyer holdings
        buyerHoldings.get(buyerID).remove(uniqueOldKey);

        // 3. Reduce capacity of new shares
        newShare.reduceCapacity(shareCount);

        // 4. Add new shares to buyer holdings
        buyerHoldings.get(buyerID).put(uniqueNewKey, shareCount);

        logAction("Local Swap", "BuyerID: " + buyerID + ", OldShareID: " + oldShareID +
                ", NewShareID: " + newShareID + ", Quantity: " + shareCount, true);
        return "Swap successful. Swapped " + shareCount + " of " + uniqueOldKey +
                " for " + shareCount + " of " + uniqueNewKey;
    }

    // Helper method for cross-market swap
    private String performCrossMarketSwap(String buyerID, String oldShareID, String oldShareType,
                                          String newShareID, String newShareType, int shareCount,
                                          String targetMarket) {
        try {

//            System.out.println("performCrossMarketSwap targetMarket: "+targetMarket);
            // First step: Check if target market has the requested share with enough capacity using UDP
            String checkRequest = "CHECK_SWAP_AVAILABILITY " + newShareID + " " + newShareType + " " + shareCount;
            int targetPort = getUDPPortForMarket(targetMarket);

            if (targetPort == -1) {
                logAction("Cross-Market Swap", "BuyerID: " + buyerID + ", Target: " + targetMarket, false);
                return "Swap failed. Invalid target market.";
            }

            String availabilityResponse = sendUDPRequest("localhost", targetPort, checkRequest);

            if (!availabilityResponse.startsWith("AVAILABLE")) {
                logAction("Cross-Market Swap", "BuyerID: " + buyerID + ", Target: " + targetMarket, false);
                return "Swap failed. " + availabilityResponse;
            }

            // Second step: Execute the swap
            String swapRequest = "EXECUTE_SWAP " + buyerID + " " + oldShareID + " " + oldShareType +
                    " " + newShareID + " " + newShareType + " " + shareCount;
            String swapResponse = sendUDPRequest("localhost", targetPort, swapRequest);

            if (swapResponse.startsWith("SUCCESS")) {
                // Update local state - remove old shares from buyer
                String uniqueOldKey = oldShareType + "-" + oldShareID;
                buyerHoldings.get(buyerID).remove(uniqueOldKey);

                // Return old shares to pool
                shareDatabase.get(oldShareType).get(oldShareID).increaseCapacity(shareCount);

                logAction("Cross-Market Swap", "BuyerID: " + buyerID + ", OldShareID: " + oldShareID +
                        ", NewShareID: " + newShareID + ", Target: " + targetMarket, true);
                return "Cross-market swap successful. " + swapResponse.substring(8);
            } else {
                logAction("Cross-Market Swap", "BuyerID: " + buyerID + ", Target: " + targetMarket, false);
                return "Swap failed. " + swapResponse;
            }
        } catch (Exception e) {
            logAction("Cross-Market Swap", "BuyerID: " + buyerID + ", Error: " + e.getMessage(), false);
            return "Cross-market swap failed: " + e.getMessage();
        }
    }

    private String sendUDPRequest(String host, int port, String message) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] buffer = message.getBytes();
            InetAddress serverAddress = InetAddress.getByName(host);

            DatagramPacket request = new DatagramPacket(buffer, buffer.length, serverAddress, port);
            socket.send(request);

            byte[] responseBuffer = new byte[4096];
            DatagramPacket response = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(response);

            return new String(response.getData(), 0, response.getLength());
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private ShareMarket.Server getRemoteServerRef(String marketName) {
        try {
            // Look up in CORBA Naming Service

//            System.out.println("getRemoteServerRef marketName: "+marketName);
            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContext ncRef = NamingContextHelper.narrow(objRef);

//            System.out.println("assigning name in getRemoteServerRef marketName: "+marketName);
            NameComponent[] name = { new NameComponent(marketName, "") };
            org.omg.CORBA.Object obj = ncRef.resolve(name);

            return ShareMarket.ServerHelper.narrow(obj);
        } catch (Exception e) {
            System.err.println("ERROR: Could not get remote server reference for " + marketName);
            e.printStackTrace();
            return null;
        }
    }

    // Helper method to get UDP port for a market
    private static int getUDPPortForMarket(String marketName) {
        switch (marketName) {
            case "NewYork": return 5000;
            case "London": return 5001;
            case "Tokyo": return 5002;
            default: return -1;
        }
    }
}