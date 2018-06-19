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

package org.sufficientlysecure.keychain.provider;

import android.net.Uri;
import android.provider.BaseColumns;

import org.sufficientlysecure.keychain.Constants;

public class KeychainContract {

    interface KeyRingsColumns {
        String MASTER_KEY_ID = "master_key_id"; // not a database id
        String KEY_RING_DATA = "key_ring_data"; // PGPPublicKeyRing / PGPSecretKeyRing blob
    }

    interface KeysColumns {
        String MASTER_KEY_ID = "master_key_id"; // not a database id
        String RANK = "rank";

        String KEY_ID = "key_id"; // not a database id
        String ALGORITHM = "algorithm";
        String FINGERPRINT = "fingerprint";

        String KEY_SIZE = "key_size";
        String KEY_CURVE_OID = "key_curve_oid";
        String CAN_SIGN = "can_sign";
        String CAN_ENCRYPT = "can_encrypt";
        String CAN_CERTIFY = "can_certify";
        String CAN_AUTHENTICATE = "can_authenticate";
        String IS_REVOKED = "is_revoked";
        String IS_SECURE = "is_secure";
        String HAS_SECRET = "has_secret";

        String CREATION = "creation";
        String EXPIRY = "expiry";
    }

    interface UpdatedKeysColumns {
        String MASTER_KEY_ID = "master_key_id"; // not a database id
        String LAST_UPDATED = "last_updated"; // time since epoch in seconds
        String SEEN_ON_KEYSERVERS = "seen_on_keyservers";
        String FINGERPRINT = "fingerprint";
    }

    interface KeySignaturesColumns {
        String MASTER_KEY_ID = "master_key_id"; // not a database id
        String SIGNER_KEY_ID = "signer_key_id";
    }

    interface UserPacketsColumns {
        String MASTER_KEY_ID = "master_key_id"; // foreign key to key_rings._ID
        String TYPE = "type"; // not a database id
        String USER_ID = "user_id"; // not a database id
        String NAME = "name";
        String EMAIL = "email";
        String COMMENT = "comment";
        String ATTRIBUTE_DATA = "attribute_data"; // not a database id
        String RANK = "rank"; // ONLY used for sorting! no key, no nothing!
        String IS_PRIMARY = "is_primary";
        String IS_REVOKED = "is_revoked";
    }

    interface CertsColumns {
        String MASTER_KEY_ID = "master_key_id";
        String RANK = "rank";
        String KEY_ID_CERTIFIER = "key_id_certifier";
        String TYPE = "type";
        String VERIFIED = "verified";
        String CREATION = "creation";
        String DATA = "data";
    }

    interface ApiAppsColumns {
        String PACKAGE_NAME = "package_name";
        String PACKAGE_CERTIFICATE = "package_signature";
    }

    interface ApiAppsAllowedKeysColumns {
        String KEY_ID = "key_id"; // not a database id
        String PACKAGE_NAME = "package_name"; // foreign key to api_apps.package_name
    }

    interface OverriddenWarnings {
        String IDENTIFIER = "identifier";
    }

    interface ApiAutocryptPeerColumns {
        String PACKAGE_NAME = "package_name";
        String IDENTIFIER = "identifier";
        String LAST_SEEN = "last_seen";

        String MASTER_KEY_ID = "master_key_id";
        String LAST_SEEN_KEY = "last_seen_key";
        String IS_MUTUAL = "is_mutual";

        String GOSSIP_MASTER_KEY_ID = "gossip_master_key_id";
        String GOSSIP_LAST_SEEN_KEY = "gossip_last_seen_key";
        String GOSSIP_ORIGIN = "gossip_origin";
    }

    public static final String CONTENT_AUTHORITY = Constants.PROVIDER_AUTHORITY;

    private static final Uri BASE_CONTENT_URI_INTERNAL = Uri
            .parse("content://" + CONTENT_AUTHORITY);

    public static final String BASE_KEY_RINGS = "key_rings";

    public static final String BASE_UPDATED_KEYS = "updated_keys";

    public static final String BASE_KEY_SIGNATURES = "key_signatures";

    public static final String PATH_UNIFIED = "unified";

    public static final String PATH_FIND = "find";
    public static final String PATH_BY_EMAIL = "email";
    public static final String PATH_BY_SUBKEY = "subkey";
    public static final String PATH_BY_USER_ID = "user_id";

    public static final String PATH_FILTER = "filter";
    public static final String PATH_BY_SIGNER = "signer";

    public static final String PATH_PUBLIC = "public";
    public static final String PATH_USER_IDS = "user_ids";
    public static final String PATH_LINKED_IDS = "linked_ids";
    public static final String PATH_KEYS = "keys";
    public static final String PATH_CERTS = "certs";

    public static final String PATH_BY_PACKAGE_NAME = "by_package_name";
    public static final String PATH_BY_KEY_ID = "by_key_id";

    public static final String BASE_AUTOCRYPT_PEERS = "autocrypt_peers";

    public static class KeyRings implements BaseColumns, KeysColumns, UserPacketsColumns {
        public static final String MASTER_KEY_ID = KeysColumns.MASTER_KEY_ID;
        public static final String IS_REVOKED = KeysColumns.IS_REVOKED;
        public static final String IS_SECURE = KeysColumns.IS_SECURE;
        public static final String VERIFIED = CertsColumns.VERIFIED;
        public static final String IS_EXPIRED = "is_expired";
        public static final String HAS_ANY_SECRET = "has_any_secret";
        public static final String HAS_ENCRYPT = "has_encrypt";
        public static final String HAS_SIGN_SECRET = "has_sign_secret";
        public static final String HAS_CERTIFY_SECRET = "has_certify_secret";
        public static final String HAS_AUTHENTICATE = "has_authenticate";
        public static final String HAS_AUTHENTICATE_SECRET = "has_authenticate_secret";
        public static final String HAS_DUPLICATE_USER_ID = "has_duplicate_user_id";
        public static final String API_KNOWN_TO_PACKAGE_NAMES = "known_to_apps";

        public static final Uri CONTENT_URI = BASE_CONTENT_URI_INTERNAL.buildUpon()
                .appendPath(BASE_KEY_RINGS).build();

        public static final String CONTENT_TYPE
                = "vnd.android.cursor.dir/vnd.org.sufficientlysecure.keychain.provider.key_rings";
        public static final String CONTENT_ITEM_TYPE
                = "vnd.android.cursor.item/vnd.org.sufficientlysecure.keychain.provider.key_rings";

        public static Uri buildUnifiedKeyRingsUri() {
            return CONTENT_URI.buildUpon().appendPath(PATH_UNIFIED).build();
        }

        public static Uri buildGenericKeyRingUri(long masterKeyId) {
            return CONTENT_URI.buildUpon().appendPath(Long.toString(masterKeyId)).build();
        }

        public static Uri buildGenericKeyRingUri(String masterKeyId) {
            return CONTENT_URI.buildUpon().appendPath(masterKeyId).build();
        }

        public static Uri buildGenericKeyRingUri(Uri uri) {
            return CONTENT_URI.buildUpon().appendPath(uri.getPathSegments().get(1)).build();
        }

        public static Uri buildUnifiedKeyRingUri(long masterKeyId) {
            return CONTENT_URI.buildUpon().appendPath(Long.toString(masterKeyId))
                    .appendPath(PATH_UNIFIED).build();
        }

        public static Uri buildUnifiedKeyRingUri(Uri uri) {
            return CONTENT_URI.buildUpon().appendPath(uri.getPathSegments().get(1))
                    .appendPath(PATH_UNIFIED).build();
        }

        public static Uri buildUnifiedKeyRingsFindByEmailUri(String email) {
            return CONTENT_URI.buildUpon().appendPath(PATH_FIND)
                    .appendPath(PATH_BY_EMAIL).appendPath(email).build();
        }

        public static Uri buildUnifiedKeyRingsFindByUserIdUri(String query) {
            return CONTENT_URI.buildUpon().appendPath(PATH_FIND)
                    .appendPath(PATH_BY_USER_ID).appendPath(query).build();
        }

        public static Uri buildUnifiedKeyRingsFindBySubkeyUri(long subkey) {
            return CONTENT_URI.buildUpon().appendPath(PATH_FIND)
                    .appendPath(PATH_BY_SUBKEY).appendPath(Long.toString(subkey)).build();
        }

        public static Uri buildUnifiedKeyRingsFilterBySigner() {
            return CONTENT_URI.buildUpon().appendPath(PATH_FILTER).appendPath(PATH_BY_SIGNER).build();
        }
    }

    public static class KeyRingData implements KeyRingsColumns, BaseColumns {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI_INTERNAL.buildUpon()
                .appendPath(BASE_KEY_RINGS).build();

        public static final String CONTENT_TYPE
                = "vnd.android.cursor.dir/vnd.org.sufficientlysecure.keychain.provider.key_ring_data";
        public static final String CONTENT_ITEM_TYPE
                = "vnd.android.cursor.item/vnd.org.sufficientlysecure.keychain.provider.key_ring_data";

        public static Uri buildPublicKeyRingUri() {
            return CONTENT_URI.buildUpon().appendPath(PATH_PUBLIC).build();
        }

        public static Uri buildPublicKeyRingUri(long masterKeyId) {
            return CONTENT_URI.buildUpon().appendPath(Long.toString(masterKeyId)).appendPath(PATH_PUBLIC).build();
        }

        public static Uri buildPublicKeyRingUri(Uri uri) {
            return CONTENT_URI.buildUpon().appendPath(uri.getPathSegments().get(1)).appendPath(PATH_PUBLIC).build();
        }
    }

    public static class Keys implements KeysColumns, BaseColumns {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI_INTERNAL.buildUpon()
                .appendPath(BASE_KEY_RINGS).build();

        /**
         * Use if multiple items get returned
         */
        public static final String CONTENT_TYPE
                = "vnd.android.cursor.dir/vnd.org.sufficientlysecure.keychain.provider.keychain.keys";

        /**
         * Use if a single item is returned
         */
        public static final String CONTENT_ITEM_TYPE
                = "vnd.android.cursor.item/vnd.org.sufficientlysecure.keychain.provider.keychain.keys";

        public static Uri buildKeysUri(long masterKeyId) {
            return CONTENT_URI.buildUpon().appendPath(Long.toString(masterKeyId)).appendPath(PATH_KEYS).build();
        }

        public static Uri buildKeysUri(Uri uri) {
            return CONTENT_URI.buildUpon().appendPath(uri.getPathSegments().get(1)).appendPath(PATH_KEYS).build();
        }

    }

    public static class KeySignatures implements KeySignaturesColumns, BaseColumns {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI_INTERNAL.buildUpon()
                .appendPath(BASE_KEY_SIGNATURES).build();

        public static final String CONTENT_TYPE
                = "vnd.android.cursor.dir/vnd.org.sufficientlysecure.keychain.provider.key_signatures";
    }

    public static class UserPackets implements UserPacketsColumns, BaseColumns {
        public static final String VERIFIED = "verified";
        public static final Uri CONTENT_URI = BASE_CONTENT_URI_INTERNAL.buildUpon()
                .appendPath(BASE_KEY_RINGS).build();

        /**
         * Use if multiple items get returned
         */
        public static final String CONTENT_TYPE
                = "vnd.android.cursor.dir/vnd.org.sufficientlysecure.keychain.provider.user_ids";

        /**
         * Use if a single item is returned
         */
        public static final String CONTENT_ITEM_TYPE
                = "vnd.android.cursor.item/vnd.org.sufficientlysecure.keychain.provider.user_ids";

        public static Uri buildUserIdsUri() {
            return CONTENT_URI.buildUpon().appendPath(PATH_USER_IDS).build();
        }

        public static Uri buildUserIdsUri(long masterKeyId) {
            return CONTENT_URI.buildUpon().appendPath(Long.toString(masterKeyId)).appendPath(PATH_USER_IDS).build();
        }

        public static Uri buildUserIdsUri(Uri uri) {
            return CONTENT_URI.buildUpon().appendPath(uri.getPathSegments().get(1)).appendPath(PATH_USER_IDS).build();
        }

        public static Uri buildLinkedIdsUri(long masterKeyId) {
            return CONTENT_URI.buildUpon().appendPath(Long.toString(masterKeyId)).appendPath(PATH_LINKED_IDS).build();
        }

        public static Uri buildLinkedIdsUri(Uri uri) {
            return CONTENT_URI.buildUpon().appendPath(uri.getPathSegments().get(1)).appendPath(PATH_LINKED_IDS).build();
        }

    }

    public static class ApiAutocryptPeer implements ApiAutocryptPeerColumns, BaseColumns {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI_INTERNAL.buildUpon()
                .appendPath(BASE_AUTOCRYPT_PEERS).build();
        public static final String KEY_IS_REVOKED = "key_is_revoked";
        public static final String KEY_IS_EXPIRED = "key_is_expired";
        public static final String KEY_IS_VERIFIED = "key_is_verified";
        public static final String GOSSIP_KEY_IS_REVOKED = "gossip_key_is_revoked";
        public static final String GOSSIP_KEY_IS_EXPIRED = "gossip_key_is_expired";
        public static final String GOSSIP_KEY_IS_VERIFIED = "gossip_key_is_verified";

        public static final int GOSSIP_ORIGIN_AUTOCRYPT = 0;
        public static final int GOSSIP_ORIGIN_SIGNATURE = 10;
        public static final int GOSSIP_ORIGIN_DEDUP = 20;

        public static Uri buildByKeyUri(Uri uri) {
            return CONTENT_URI.buildUpon().appendPath(PATH_BY_KEY_ID).appendPath(uri.getPathSegments().get(1)).build();
        }

        public static Uri buildByPackageName(String packageName) {
            return CONTENT_URI.buildUpon().appendPath(PATH_BY_PACKAGE_NAME).appendPath(packageName).build();
        }

        public static Uri buildByPackageNameAndAutocryptId(String packageName, String autocryptPeer) {
            return CONTENT_URI.buildUpon().appendPath(PATH_BY_PACKAGE_NAME).appendPath(packageName).appendPath(autocryptPeer).build();
        }

        public static Uri buildByMasterKeyId(long masterKeyId) {
            return CONTENT_URI.buildUpon().appendPath(PATH_BY_KEY_ID).appendPath(Long.toString(masterKeyId)).build();
        }
    }

    public static class Certs implements CertsColumns, BaseColumns {
        public static final String USER_ID = UserPacketsColumns.USER_ID;
        public static final String NAME = UserPacketsColumns.NAME;
        public static final String EMAIL = UserPacketsColumns.EMAIL;
        public static final String COMMENT = UserPacketsColumns.COMMENT;
        public static final String SIGNER_UID = "signer_user_id";

        public static final int UNVERIFIED = 0;
        public static final int VERIFIED_SECRET = 1;
        public static final int VERIFIED_SELF = 2;

        public static final Uri CONTENT_URI = BASE_CONTENT_URI_INTERNAL.buildUpon()
                .appendPath(BASE_KEY_RINGS).build();

        public static Uri buildCertsUri(long masterKeyId) {
            return CONTENT_URI.buildUpon().appendPath(Long.toString(masterKeyId)).appendPath(PATH_CERTS).build();
        }

        public static Uri buildCertsSpecificUri(long masterKeyId, long rank, long certifier) {
            return CONTENT_URI.buildUpon().appendPath(Long.toString(masterKeyId))
                    .appendPath(PATH_CERTS).appendPath(Long.toString(rank))
                    .appendPath(Long.toString(certifier)).build();
        }

        public static Uri buildCertsUri(Uri uri) {
            return CONTENT_URI.buildUpon().appendPath(uri.getPathSegments().get(1))
                    .appendPath(PATH_CERTS).build();
        }

        public static Uri buildLinkedIdCertsUri(Uri uri, int rank) {
            return CONTENT_URI.buildUpon().appendPath(uri.getPathSegments().get(1))
                    .appendPath(PATH_LINKED_IDS).appendPath(Integer.toString(rank))
                    .appendPath(PATH_CERTS).build();
        }

    }

    private KeychainContract() {
    }
}
