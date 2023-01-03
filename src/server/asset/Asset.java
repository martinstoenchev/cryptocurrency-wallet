package server.asset;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Asset {

    @SerializedName("asset_id")
    private final String assetID;
    private final String name;
    @SerializedName("price_usd")
    private final double priceUSD;

    public Asset(String assetID, String name, double priceUSD) {
        this.assetID = assetID;
        this.name = name;
        this.priceUSD = priceUSD;
    }

    public double getPriceUSD() {
        return priceUSD;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Asset asset = (Asset) o;
        return assetID.equals(asset.assetID) && name.equals(asset.name) && priceUSD == asset.priceUSD;
    }

    @Override
    public int hashCode() {
        return Objects.hash(assetID, name, priceUSD);
    }

    @Override
    public String toString() {
        return String.format("[ Asset ID: %s, Asset name: %s, USD price: %.2f ]" + System.lineSeparator(), assetID, name, priceUSD);
    }

    public static class AssetListDeserializer implements JsonDeserializer<List<Asset>> {

        @Override
        public List<Asset> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            List<Asset> list = new ArrayList<>();
            for(JsonElement e : json.getAsJsonArray()) {
                /*if(!forbiddenCities.contains(e.getAsJsonObject().get("City").getAsString())) {
                    list.add(context.deserialize(e, Student.class));
                }*/
                if (e.getAsJsonObject().get("type_is_crypto").getAsInt() == 1 && e.getAsJsonObject().get("price_usd") != null && e.getAsJsonObject().get("price_usd").getAsDouble() != 0.0) {
                    list.add(context.deserialize(e, Asset.class));
                }
            }
            return list;
        }
    }
}
