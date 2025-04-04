package server;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class Share implements Serializable {
    private final String shareID;
    private final String shareType;
    private int availableCapacity;
    private final int totalCapacity;
    private final Set<String> buyers;
    private final String originMarket;

    public Share(String shareID, String shareType, int availableCapacity, String originMarket) {
        this.shareID = shareID;
        this.shareType = shareType;
        this.availableCapacity = availableCapacity;
        this.totalCapacity = availableCapacity;
        this.buyers = new HashSet<>();
        this.originMarket = originMarket;
    }

    public Share(String shareID, String shareType, int availableCapacity) {
        this(shareID, shareType, availableCapacity, null);
    }

    // Add getter for origin market
    public String getOriginMarket() {
        return originMarket;
    }

    public int getAvailableCapacity() {
        return availableCapacity;
    }

    public void reduceCapacity(int count) {
        this.availableCapacity -= count;
    }

    public void increaseCapacity(int count) {
        this.availableCapacity += count;
    }

    public void addBuyer(String buyerID) {
        buyers.add(buyerID);
    }

    public boolean hasBuyer(String buyerID) {
        return buyers.contains(buyerID);
    }

    public void removeBuyer(String buyerID) {
        buyers.remove(buyerID);
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public String getShareID() {
        return shareID;
    }

    // **New Method** - Fix for missing method error
    public String getShareType() {
        return shareType;
    }
    public String getName() {
        return shareType;
    }


    @Override
    public String toString() {
        return "[Share ID: " + shareID + ", Type: " + shareType + ", Available: " + availableCapacity +
                (originMarket != null ? ", Market: " + originMarket : "") + "]";
    }
}