/*
 * Copyright (C) 2017 Schürmann & Breitmoser GbR
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.ui.keyview.loader;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;
import org.openintents.openpgp.util.OpenPgpApi;
import org.sufficientlysecure.keychain.linked.LinkedAttribute;
import org.sufficientlysecure.keychain.linked.UriAttribute;
import org.sufficientlysecure.keychain.model.AutocryptPeer;
import org.sufficientlysecure.keychain.provider.AutocryptPeerDao;
import org.sufficientlysecure.keychain.provider.KeychainContract.Certs;
import org.sufficientlysecure.keychain.provider.KeychainContract.UserPackets;
import org.sufficientlysecure.keychain.ui.util.PackageIconGetter;
import timber.log.Timber;


public class IdentityDao {
    private static final String[] USER_PACKETS_PROJECTION = new String[]{
            UserPackets._ID,
            UserPackets.TYPE,
            UserPackets.USER_ID,
            UserPackets.ATTRIBUTE_DATA,
            UserPackets.RANK,
            UserPackets.VERIFIED,
            UserPackets.IS_PRIMARY,
            UserPackets.IS_REVOKED,
            UserPackets.NAME,
            UserPackets.EMAIL,
            UserPackets.COMMENT,
    };
    private static final int INDEX_ID = 0;
    private static final int INDEX_TYPE = 1;
    private static final int INDEX_USER_ID = 2;
    private static final int INDEX_ATTRIBUTE_DATA = 3;
    private static final int INDEX_RANK = 4;
    private static final int INDEX_VERIFIED = 5;
    private static final int INDEX_IS_PRIMARY = 6;
    private static final int INDEX_IS_REVOKED = 7;
    private static final int INDEX_NAME = 8;
    private static final int INDEX_EMAIL = 9;
    private static final int INDEX_COMMENT = 10;

    private static final String USER_IDS_WHERE = UserPackets.IS_REVOKED + " = 0";


    private final ContentResolver contentResolver;
    private final PackageIconGetter packageIconGetter;
    private final PackageManager packageManager;
    private final AutocryptPeerDao autocryptPeerDao;

    static IdentityDao getInstance(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        PackageManager packageManager = context.getPackageManager();
        PackageIconGetter iconGetter = PackageIconGetter.getInstance(context);
        AutocryptPeerDao autocryptPeerDao = AutocryptPeerDao.getInstance(context);
        return new IdentityDao(contentResolver, packageManager, iconGetter, autocryptPeerDao);
    }

    private IdentityDao(ContentResolver contentResolver, PackageManager packageManager, PackageIconGetter iconGetter,
            AutocryptPeerDao autocryptPeerDao) {
        this.packageManager = packageManager;
        this.contentResolver = contentResolver;
        this.packageIconGetter = iconGetter;
        this.autocryptPeerDao = autocryptPeerDao;
    }

    List<IdentityInfo> getIdentityInfos(long masterKeyId, boolean showLinkedIds) {
        ArrayList<IdentityInfo> identities = new ArrayList<>();

        if (showLinkedIds) {
            loadLinkedIds(identities, masterKeyId);
        }
        loadUserIds(identities, masterKeyId);
        correlateOrAddAutocryptPeers(identities, masterKeyId);

        return Collections.unmodifiableList(identities);
    }

    private void correlateOrAddAutocryptPeers(ArrayList<IdentityInfo> identities, long masterKeyId) {
        for (AutocryptPeer autocryptPeer : autocryptPeerDao.getAutocryptPeersForKey(masterKeyId)) {
            String packageName = autocryptPeer.package_name();
            String autocryptId = autocryptPeer.identifier();

            Drawable drawable = packageIconGetter.getDrawableForPackageName(packageName);
            Intent autocryptPeerIntent = getAutocryptPeerActivityIntentIfResolvable(packageName, autocryptId);

            UserIdInfo associatedUserIdInfo = findUserIdMatchingAutocryptPeer(identities, autocryptId);
            if (associatedUserIdInfo != null) {
                int position = identities.indexOf(associatedUserIdInfo);
                AutocryptPeerInfo autocryptPeerInfo = AutocryptPeerInfo
                        .create(associatedUserIdInfo, autocryptId, packageName, drawable, autocryptPeerIntent);
                identities.set(position, autocryptPeerInfo);
            } else {
                AutocryptPeerInfo autocryptPeerInfo = AutocryptPeerInfo
                        .create(autocryptId, packageName, drawable, autocryptPeerIntent);
                identities.add(autocryptPeerInfo);
            }
        }
    }

    private Intent getAutocryptPeerActivityIntentIfResolvable(String packageName, String autocryptPeer) {
        Intent intent = new Intent();
        intent.setAction("org.autocrypt.PEER_ACTION");
        intent.setPackage(packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(OpenPgpApi.EXTRA_AUTOCRYPT_PEER_ID, autocryptPeer);

        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(intent, 0);
        if (resolveInfos != null && !resolveInfos.isEmpty()) {
            return intent;
        } else {
            return null;
        }
    }

    private static UserIdInfo findUserIdMatchingAutocryptPeer(List<IdentityInfo> identities, String autocryptPeer) {
        for (IdentityInfo identityInfo : identities) {
            if (identityInfo instanceof UserIdInfo) {
                UserIdInfo userIdInfo = (UserIdInfo) identityInfo;
                if (autocryptPeer.equals(userIdInfo.getEmail())) {
                    return userIdInfo;
                }
            }
        }
        return null;
    }

    private void loadLinkedIds(ArrayList<IdentityInfo> identities, long masterKeyId) {
        Cursor cursor = contentResolver.query(UserPackets.buildLinkedIdsUri(masterKeyId),
                USER_PACKETS_PROJECTION, USER_IDS_WHERE, null, null);
        if (cursor == null) {
            Timber.e("Error loading key items!");
            return;
        }

        try {
            while (cursor.moveToNext()) {
                int rank = cursor.getInt(INDEX_RANK);
                int verified = cursor.getInt(INDEX_VERIFIED);
                boolean isPrimary = cursor.getInt(INDEX_IS_PRIMARY) != 0;

                byte[] data = cursor.getBlob(INDEX_ATTRIBUTE_DATA);
                try {
                    UriAttribute uriAttribute = LinkedAttribute.fromAttributeData(data);
                    if (uriAttribute instanceof LinkedAttribute) {
                        LinkedIdInfo identityInfo = LinkedIdInfo.create(rank, verified, isPrimary, uriAttribute);
                        identities.add(identityInfo);
                    }
                } catch (IOException e) {
                    Timber.e(e, "Failed parsing uri attribute");
                }
            }
        } finally {
            cursor.close();
        }
    }

    private void loadUserIds(ArrayList<IdentityInfo> identities, long masterKeyId) {
        Cursor cursor = contentResolver.query(UserPackets.buildUserIdsUri(masterKeyId),
                USER_PACKETS_PROJECTION, USER_IDS_WHERE, null, null);
        if (cursor == null) {
            Timber.e("Error loading key items!");
            return;
        }

        try {
            while (cursor.moveToNext()) {
                int rank = cursor.getInt(INDEX_RANK);
                int verified = cursor.getInt(INDEX_VERIFIED);
                boolean isPrimary = cursor.getInt(INDEX_IS_PRIMARY) != 0;

                if (!cursor.isNull(INDEX_NAME) || !cursor.isNull(INDEX_EMAIL)) {
                    String name = cursor.getString(INDEX_NAME);
                    String email = cursor.getString(INDEX_EMAIL);
                    String comment = cursor.getString(INDEX_COMMENT);

                    IdentityInfo identityInfo = UserIdInfo.create(rank, verified, isPrimary, name, email, comment);
                    identities.add(identityInfo);
                }
            }
        } finally {
            cursor.close();
        }
    }

    public interface IdentityInfo {
        int getRank();
        int getVerified();
        boolean isPrimary();
    }

    @AutoValue
    public abstract static class UserIdInfo implements IdentityInfo {
        public abstract int getRank();
        public abstract int getVerified();
        public abstract boolean isPrimary();

        @Nullable
        public abstract String getName();
        @Nullable
        public abstract String getEmail();
        @Nullable
        public abstract String getComment();

        static UserIdInfo create(int rank, int verified, boolean isPrimary, String name, String email,
                String comment) {
            return new AutoValue_IdentityDao_UserIdInfo(rank, verified, isPrimary, name, email, comment);
        }
    }

    @AutoValue
    public abstract static class LinkedIdInfo implements IdentityInfo {
        public abstract int getRank();
        public abstract int getVerified();
        public abstract boolean isPrimary();

        public abstract UriAttribute getUriAttribute();

        static LinkedIdInfo create(int rank, int verified, boolean isPrimary, UriAttribute uriAttribute) {
            return new AutoValue_IdentityDao_LinkedIdInfo(rank, verified, isPrimary, uriAttribute);
        }
    }

    @AutoValue
    public abstract static class AutocryptPeerInfo implements IdentityInfo {
        public abstract int getRank();
        public abstract int getVerified();
        public abstract boolean isPrimary();

        public abstract String getIdentity();
        public abstract String getPackageName();
        @Nullable
        public abstract Drawable getAppIcon();
        @Nullable
        public abstract UserIdInfo getUserIdInfo();
        @Nullable
        public abstract Intent getAutocryptPeerIntent();

        static AutocryptPeerInfo create(UserIdInfo userIdInfo, String autocryptPeer, String packageName,
                Drawable appIcon, Intent autocryptPeerIntent) {
            return new AutoValue_IdentityDao_AutocryptPeerInfo(userIdInfo.getRank(), userIdInfo.getVerified(),
                    userIdInfo.isPrimary(), autocryptPeer, packageName, appIcon, userIdInfo, autocryptPeerIntent);
        }

        static AutocryptPeerInfo create(String autocryptPeer, String packageName, Drawable appIcon, Intent autocryptPeerIntent) {
            return new AutoValue_IdentityDao_AutocryptPeerInfo(
                    0, Certs.VERIFIED_SELF, false, autocryptPeer, packageName, appIcon, null, autocryptPeerIntent);
        }
    }

}
