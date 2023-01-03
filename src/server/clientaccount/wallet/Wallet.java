package server.clientaccount.wallet;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Wallet {

    // Asset ID: BTC, Asset quantity: 0.606664670904
    private static final int INDEX_OF_ASSET_ID_VALUE = 10;
    private static final int INDEX_OF_ASSET_QUANTITY_VALUE = 16;

    private double balance;
    private final Map<String, Double> assets; // <AssetID, Quantity> TODO: should get the info from database file

    public Wallet(double balance) {
        this.balance = balance;
        this.assets = new HashMap<>();
    }

    public void setAssets(String assetsAsString) {
        String temp = assetsAsString.substring(2, assetsAsString.length() - 2); // removing "{ " and " }"
        String[] stringAssets = temp.split(",");

        for (String stringAsset : stringAssets) {
            String temp2 = stringAsset.substring(1, stringAsset.length() - 1); // removing "[" and "]"
            String[] tokens = temp2.split(" \\| ");
            String assetID = tokens[0].substring(INDEX_OF_ASSET_ID_VALUE);
            double quantity = Double.parseDouble(tokens[1].substring(INDEX_OF_ASSET_QUANTITY_VALUE));

            this.assets.put(assetID, quantity);
        }
    }

    public double getBalance() {
        return balance;
    }

    public double depositMoney(double amount) {
        balance += amount;
        return balance;
    }

    public boolean buyAsset(String assetID, double amount, double price) {
        if (balance < amount) {
            return false;
        }

        balance -= amount;
        double quantity;

        if (!assets.containsKey(assetID)) {
            quantity = amount / price;
        } else {
            quantity = assets.get(assetID);
            quantity += (amount / price);
        }

        assets.put(assetID, quantity);

        return true;
    }

    public boolean sellAsset(String assetID, double price) {
        if (!assets.containsKey(assetID)) {
            return false;
        }

        double quantity = assets.get(assetID);
        balance += (quantity*price);
        assets.remove(assetID);

        return true;
    }

    public String getAssets() {
        StringBuilder sb = new StringBuilder();
        sb.append("{ ");

        for (Map.Entry<String, Double> entry : assets.entrySet()) {
            sb.append("[Asset ID: %s | Asset quantity: %.12f],".formatted(entry.getKey(), entry.getValue()));
        }

        sb.deleteCharAt(sb.length() - 1);
        sb.append(" }");

        return sb.toString();
    }
}
