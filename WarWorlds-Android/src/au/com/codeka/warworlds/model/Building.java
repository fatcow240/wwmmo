package au.com.codeka.warworlds.model;

import android.os.Parcel;
import android.os.Parcelable;
import au.com.codeka.warworlds.model.protobuf.Messages;

/**
 * Represents a single building on a colony.
 */
public class Building implements Parcelable {
    private String mKey;
    private String mColonyKey;
    private String mDesignName;
    private int mLevel;

    public String getKey() {
        return mKey;
    }
    public String getColonyKey() {
        return mColonyKey;
    }
    public String getDesignName() {
        return mDesignName;
    }
    public int getLevel() {
        return mLevel;
    }
    public BuildingDesign getDesign() {
        return BuildingDesignManager.getInstance().getDesign(mDesignName);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mKey);
        parcel.writeString(mColonyKey);
        parcel.writeString(mDesignName);
        parcel.writeInt(mLevel);
    }

    public static final Parcelable.Creator<Building> CREATOR
                = new Parcelable.Creator<Building>() {
        @Override
        public Building createFromParcel(Parcel parcel) {
            Building b = new Building();
            b.mKey = parcel.readString();
            b.mColonyKey = parcel.readString();
            b.mDesignName = parcel.readString();
            b.mLevel = parcel.readInt();
            return b;
        }

        @Override
        public Building[] newArray(int size) {
            return new Building[size];
        }
    };

    public static Building fromProtocolBuffer(Messages.Building pb) {
        Building building = new Building();
        building.mKey = pb.getKey();
        building.mColonyKey = pb.getColonyKey();
        building.mDesignName = pb.getDesignName();
        building.mLevel = pb.getLevel();
        return building;
    }
}
