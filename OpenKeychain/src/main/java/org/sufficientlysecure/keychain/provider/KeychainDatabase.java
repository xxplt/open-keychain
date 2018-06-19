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


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.db.SupportSQLiteOpenHelper;
import android.arch.persistence.db.SupportSQLiteOpenHelper.Callback;
import android.arch.persistence.db.SupportSQLiteOpenHelper.Configuration;
import android.arch.persistence.db.framework.FrameworkSQLiteOpenHelperFactory;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.provider.BaseColumns;

import org.sufficientlysecure.keychain.ApiAppsModel;
import org.sufficientlysecure.keychain.AutocryptPeersModel;
import org.sufficientlysecure.keychain.CertsModel;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.KeyMetadataModel;
import org.sufficientlysecure.keychain.KeyRingsPublicModel;
import org.sufficientlysecure.keychain.UserPacketsModel;
import org.sufficientlysecure.keychain.model.ApiApp;
import org.sufficientlysecure.keychain.model.Certification;
import org.sufficientlysecure.keychain.provider.KeychainContract.ApiAppsAllowedKeysColumns;
import org.sufficientlysecure.keychain.provider.KeychainContract.CertsColumns;
import org.sufficientlysecure.keychain.provider.KeychainContract.KeyRingsColumns;
import org.sufficientlysecure.keychain.provider.KeychainContract.KeySignaturesColumns;
import org.sufficientlysecure.keychain.provider.KeychainContract.KeysColumns;
import org.sufficientlysecure.keychain.provider.KeychainContract.OverriddenWarnings;
import org.sufficientlysecure.keychain.provider.KeychainContract.UserPacketsColumns;
import org.sufficientlysecure.keychain.util.Preferences;
import timber.log.Timber;


/**
 * SQLite Datatypes (from http://www.sqlite.org/datatype3.html)
 * - NULL. The value is a NULL value.
 * - INTEGER. The value is a signed integer, stored in 1, 2, 3, 4, 6, or 8 bytes depending on the magnitude of the value.
 * - REAL. The value is a floating point value, stored as an 8-byte IEEE floating point number.
 * - TEXT. The value is a text string, stored using the database encoding (UTF-8, UTF-16BE or UTF-16LE).
 * - BLOB. The value is a blob of data, stored exactly as it was input.
 */
public class KeychainDatabase {
    private static final String DATABASE_NAME = "openkeychain.db";
    private static final int DATABASE_VERSION = 28;
    private final SupportSQLiteOpenHelper supportSQLiteOpenHelper;
    private Context context;

    public interface Tables {
        String KEY_RINGS_PUBLIC = "keyrings_public";
        String KEYS = "keys";
        String KEY_SIGNATURES = "key_signatures";
        String USER_PACKETS = "user_packets";
        String CERTS = "certs";
        String API_ALLOWED_KEYS = "api_allowed_keys";
        String OVERRIDDEN_WARNINGS = "overridden_warnings";
    }

    private static final String CREATE_KEYS =
            "CREATE TABLE IF NOT EXISTS " + Tables.KEYS + " ("
                + KeysColumns.MASTER_KEY_ID + " INTEGER, "
                + KeysColumns.RANK + " INTEGER, "

                + KeysColumns.KEY_ID + " INTEGER, "
                + KeysColumns.KEY_SIZE + " INTEGER, "
                + KeysColumns.KEY_CURVE_OID + " TEXT, "
                + KeysColumns.ALGORITHM + " INTEGER, "
                + KeysColumns.FINGERPRINT + " BLOB, "

                + KeysColumns.CAN_CERTIFY + " INTEGER, "
                + KeysColumns.CAN_SIGN + " INTEGER, "
                + KeysColumns.CAN_ENCRYPT + " INTEGER, "
                + KeysColumns.CAN_AUTHENTICATE + " INTEGER, "
                + KeysColumns.IS_REVOKED + " INTEGER, "
                + KeysColumns.HAS_SECRET + " INTEGER, "
                + KeysColumns.IS_SECURE + " INTEGER, "

                + KeysColumns.CREATION + " INTEGER, "
                + KeysColumns.EXPIRY + " INTEGER, "

                + "PRIMARY KEY(" + KeysColumns.MASTER_KEY_ID + ", " + KeysColumns.RANK + "),"
                + "FOREIGN KEY(" + KeysColumns.MASTER_KEY_ID + ") REFERENCES "
                    + Tables.KEY_RINGS_PUBLIC + "(" + KeyRingsColumns.MASTER_KEY_ID + ") ON DELETE CASCADE"
            + ")";

    private static final String CREATE_USER_PACKETS =
            "CREATE TABLE IF NOT EXISTS " + Tables.USER_PACKETS + "("
                + UserPacketsColumns.MASTER_KEY_ID + " INTEGER, "
                + UserPacketsColumns.TYPE + " INT, "
                + UserPacketsColumns.USER_ID + " TEXT, "
                + UserPacketsColumns.NAME + " TEXT, "
                + UserPacketsColumns.EMAIL + " TEXT, "
                + UserPacketsColumns.COMMENT + " TEXT, "
                + UserPacketsColumns.ATTRIBUTE_DATA + " BLOB, "

                + UserPacketsColumns.IS_PRIMARY + " INTEGER, "
                + UserPacketsColumns.IS_REVOKED + " INTEGER, "
                + UserPacketsColumns.RANK+ " INTEGER, "

                + "PRIMARY KEY(" + UserPacketsColumns.MASTER_KEY_ID + ", " + UserPacketsColumns.RANK + "), "
                + "FOREIGN KEY(" + UserPacketsColumns.MASTER_KEY_ID + ") REFERENCES "
                    + Tables.KEY_RINGS_PUBLIC + "(" + KeyRingsColumns.MASTER_KEY_ID + ") ON DELETE CASCADE"
            + ")";

    private static final String CREATE_KEY_SIGNATURES =
            "CREATE TABLE IF NOT EXISTS " + Tables.KEY_SIGNATURES + " ("
                    + KeySignaturesColumns.MASTER_KEY_ID + " INTEGER NOT NULL, "
                    + KeySignaturesColumns.SIGNER_KEY_ID + " INTEGER NOT NULL, "
                    + "PRIMARY KEY(" + KeySignaturesColumns.MASTER_KEY_ID + ", " + KeySignaturesColumns.SIGNER_KEY_ID + "), "
                    + "FOREIGN KEY(" + KeySignaturesColumns.MASTER_KEY_ID + ") REFERENCES "
                    + Tables.KEY_RINGS_PUBLIC + "(" + KeyRingsColumns.MASTER_KEY_ID + ") ON DELETE CASCADE"
                    + ")";

    private static final String CREATE_API_APPS_ALLOWED_KEYS =
            "CREATE TABLE IF NOT EXISTS " + Tables.API_ALLOWED_KEYS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + ApiAppsAllowedKeysColumns.KEY_ID + " INTEGER, "
                + ApiAppsAllowedKeysColumns.PACKAGE_NAME + " TEXT NOT NULL, "

                + "UNIQUE(" + ApiAppsAllowedKeysColumns.KEY_ID + ", "
                + ApiAppsAllowedKeysColumns.PACKAGE_NAME + "), "
                + "FOREIGN KEY(" + ApiAppsAllowedKeysColumns.PACKAGE_NAME + ") REFERENCES "
                + "api_apps (" + ApiAppsAllowedKeysColumns.PACKAGE_NAME + ") ON DELETE CASCADE"
                + ")";

    private static final String CREATE_OVERRIDDEN_WARNINGS =
            "CREATE TABLE IF NOT EXISTS " + Tables.OVERRIDDEN_WARNINGS + " ("
                    + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + OverriddenWarnings.IDENTIFIER + " TEXT NOT NULL UNIQUE "
                + ")";

    public KeychainDatabase(Context context) {
        this.context = context;
        supportSQLiteOpenHelper =
                new FrameworkSQLiteOpenHelperFactory()
                        .create(Configuration.builder(context).name(DATABASE_NAME).callback(
                                new Callback(DATABASE_VERSION) {
                                    @Override
                                    public void onCreate(SupportSQLiteDatabase db) {
                                        KeychainDatabase.this.onCreate(db);
                                    }

                                    @Override
                                    public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                                        KeychainDatabase.this.onUpgrade(db, oldVersion, newVersion);
                                    }

                                    @Override
                                    public void onDowngrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                                        KeychainDatabase.this.onDowngrade(db, oldVersion, newVersion);
                                    }

                                    @Override
                                    public void onOpen(SupportSQLiteDatabase db) {
                                        super.onOpen(db);
                                        if (!db.isReadOnly()) {
                                            // Enable foreign key constraints
                                            db.execSQL("PRAGMA foreign_keys=ON;");
                                        }
                                    }
                                }).build());
    }

    public SupportSQLiteDatabase getReadableDatabase() {
        return supportSQLiteOpenHelper.getReadableDatabase();
    }

    public SupportSQLiteDatabase getWritableDatabase() {
        return supportSQLiteOpenHelper.getWritableDatabase();
    }

    private void onCreate(SupportSQLiteDatabase db) {
        Timber.w("Creating database...");

        db.execSQL(KeyRingsPublicModel.CREATE_TABLE);
        db.execSQL(CREATE_KEYS);
        db.execSQL(UserPacketsModel.CREATE_TABLE);
        db.execSQL(CertsModel.CREATE_TABLE);
        db.execSQL(KeyMetadataModel.CREATE_TABLE);
        db.execSQL(CREATE_KEY_SIGNATURES);
        db.execSQL(CREATE_API_APPS_ALLOWED_KEYS);
        db.execSQL(CREATE_OVERRIDDEN_WARNINGS);
        db.execSQL(AutocryptPeersModel.CREATE_TABLE);
        db.execSQL(ApiAppsModel.CREATE_TABLE);

        db.execSQL("CREATE INDEX keys_by_rank ON keys (" + KeysColumns.RANK + ", " + KeysColumns.MASTER_KEY_ID + ");");
        db.execSQL("CREATE INDEX uids_by_rank ON user_packets (" + UserPacketsColumns.RANK + ", "
                + UserPacketsColumns.USER_ID + ", " + UserPacketsColumns.MASTER_KEY_ID + ");");
        db.execSQL("CREATE INDEX verified_certs ON certs ("
                + CertsColumns.VERIFIED + ", " + CertsColumns.MASTER_KEY_ID + ");");
        db.execSQL("CREATE INDEX uids_by_email ON user_packets ("
                + UserPacketsColumns.EMAIL + ");");

        Preferences.getPreferences(context).setKeySignaturesTableInitialized();
    }

    private void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
        Timber.d("Upgrading db from " + oldVersion + " to " + newVersion);

        switch (oldVersion) {
            case 1:
                // add has_secret for all who are upgrading from a beta version
                try {
                    db.execSQL("ALTER TABLE keys ADD COLUMN has_secret INTEGER");
                } catch (Exception e) {
                    // never mind, the column probably already existed
                }
                // fall through
            case 2:
                // ECC support
                try {
                    db.execSQL("ALTER TABLE keys ADD COLUMN key_curve_oid TEXT");
                } catch (Exception e) {
                    // never mind, the column probably already existed
                }
                // fall through
            case 3:
                // better s2k detection, we need consolidate
                // fall through
            case 4:
                try {
                    db.execSQL("ALTER TABLE keys ADD COLUMN can_authenticate INTEGER");
                } catch (Exception e) {
                    // never mind, the column probably already existed
                }
                // fall through
            case 5:
                // do consolidate for 3.0 beta3
                // fall through
            case 6:
                db.execSQL("ALTER TABLE user_ids ADD COLUMN type INTEGER");
                db.execSQL("ALTER TABLE user_ids ADD COLUMN attribute_data BLOB");
            case 7:
                // new table for allowed key ids in API
                try {
                    db.execSQL(CREATE_API_APPS_ALLOWED_KEYS);
                } catch (Exception e) {
                    // never mind, the column probably already existed
                }
            case 8:
                // tbale name for user_ids changed to user_packets
                db.execSQL("DROP TABLE IF EXISTS certs");
                db.execSQL("DROP TABLE IF EXISTS user_ids");
                db.execSQL("CREATE TABLE IF NOT EXISTS user_packets("
                        + "master_key_id INTEGER, "
                        + "type INT, "
                        + "user_id TEXT, "
                        + "attribute_data BLOB, "

                        + "is_primary INTEGER, "
                        + "is_revoked INTEGER, "
                        + "rank INTEGER, "

                        + "PRIMARY KEY(master_key_id, rank), "
                        + "FOREIGN KEY(master_key_id) REFERENCES "
                        + "keyrings_public(master_key_id) ON DELETE CASCADE"
                        + ")");
                db.execSQL("CREATE TABLE IF NOT EXISTS certs("
                        + "master_key_id INTEGER,"
                        + "rank INTEGER, " // rank of certified uid

                        + "key_id_certifier INTEGER, " // certifying key
                        + "type INTEGER, "
                        + "verified INTEGER, "
                        + "creation INTEGER, "

                        + "data BLOB, "

                        + "PRIMARY KEY(master_key_id, rank, "
                        + "key_id_certifier), "
                        + "FOREIGN KEY(master_key_id) REFERENCES "
                        + "keyrings_public(master_key_id) ON DELETE CASCADE,"
                        + "FOREIGN KEY(master_key_id, rank) REFERENCES "
                        + "user_packets(master_key_id, rank) ON DELETE CASCADE"
                        + ")");
            case 9:
                // do nothing here, just consolidate
            case 10:
                // fix problems in database, see #1402 for details
                // https://github.com/open-keychain/open-keychain/issues/1402
                // no longer needed, api_accounts is deprecated
                // db.execSQL("DELETE FROM api_accounts WHERE key_id BETWEEN 0 AND 3");
            case 11:
                db.execSQL("CREATE TABLE IF NOT EXISTS updated_keys ("
                        + "master_key_id INTEGER PRIMARY KEY, "
                        + "last_updated INTEGER, "
                        + "FOREIGN KEY(master_key_id) REFERENCES "
                        + "keyrings_public(master_key_id) ON DELETE CASCADE"
                        + ")");
            case 12:
                // do nothing here, just consolidate
            case 13:
                db.execSQL("CREATE INDEX keys_by_rank ON keys (" + KeysColumns.RANK + ");");
                db.execSQL("CREATE INDEX uids_by_rank ON user_packets (" + UserPacketsColumns.RANK + ", "
                        + UserPacketsColumns.USER_ID + ", " + UserPacketsColumns.MASTER_KEY_ID + ");");
                db.execSQL("CREATE INDEX verified_certs ON certs ("
                        + CertsColumns.VERIFIED + ", " + CertsColumns.MASTER_KEY_ID + ");");
            case 14:
                db.execSQL("ALTER TABLE user_packets ADD COLUMN name TEXT");
                db.execSQL("ALTER TABLE user_packets ADD COLUMN email TEXT");
                db.execSQL("ALTER TABLE user_packets ADD COLUMN comment TEXT");
            case 15:
                db.execSQL("CREATE INDEX uids_by_name ON user_packets (name COLLATE NOCASE)");
                db.execSQL("CREATE INDEX uids_by_email ON user_packets (email COLLATE NOCASE)");
            case 16:
                // splitUserId changed: Execute consolidate for new parsing of name, email
            case 17:
                // splitUserId changed: Execute consolidate for new parsing of name, email
            case 18:
                db.execSQL("ALTER TABLE keys ADD COLUMN is_secure INTEGER");
            case 19:
                // emergency fix for crashing consolidate
                db.execSQL("UPDATE keys SET is_secure = 1;");
            /* TODO actually drop this table. leaving it around for now!
            case 20:
                db.execSQL("DROP TABLE api_accounts");
                if (oldVersion == 20) {
                    // no need to consolidate
                    return;
                }
            */
            case 20:
                db.execSQL(
                        "CREATE TABLE IF NOT EXISTS overridden_warnings ("
                                + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                                + "identifier TEXT NOT NULL UNIQUE "
                                + ")");

            case 21:
                try {
                    db.execSQL("ALTER TABLE updated_keys ADD COLUMN seen_on_keyservers INTEGER;");
                } catch (SQLiteException e) {
                    // don't bother, the column probably already existed
                }

            case 22:
                db.execSQL("CREATE TABLE IF NOT EXISTS api_autocrypt_peers ("
                        + "package_name TEXT NOT NULL, "
                        + "identifier TEXT NOT NULL, "
                        + "last_updated INTEGER NOT NULL, "
                        + "last_seen_key INTEGER NOT NULL, "
                        + "state INTEGER NOT NULL, "
                        + "master_key_id INTEGER NULL, "
                        + "PRIMARY KEY(package_name, identifier), "
                        + "FOREIGN KEY(package_name) REFERENCES api_apps(package_name) ON DELETE CASCADE"
                        + ")");

            case 23:
                db.execSQL("CREATE TABLE IF NOT EXISTS key_signatures ("
                        + "master_key_id INTEGER NOT NULL, "
                        + "signer_key_id INTEGER NOT NULL, "
                        + "PRIMARY KEY(master_key_id, signer_key_id), "
                        + "FOREIGN KEY(master_key_id) REFERENCES keyrings_public(master_key_id) ON DELETE CASCADE"
                        + ")");

            case 24: {
                try {
                    db.beginTransaction();
                    db.execSQL("ALTER TABLE api_autocrypt_peers RENAME TO tmp");
                    db.execSQL("CREATE TABLE api_autocrypt_peers ("
                            + "package_name TEXT NOT NULL, "
                            + "identifier TEXT NOT NULL, "
                            + "last_seen INTEGER, "
                            + "last_seen_key INTEGER, "
                            + "is_mutual INTEGER, "
                            + "master_key_id INTEGER, "
                            + "gossip_master_key_id INTEGER, "
                            + "gossip_last_seen_key INTEGER, "
                            + "gossip_origin INTEGER, "
                            + "PRIMARY KEY(package_name, identifier), "
                            + "FOREIGN KEY(package_name) REFERENCES api_apps (package_name) ON DELETE CASCADE"
                            + ")");
                    // Note: Keys from Autocrypt 0.X with state == "reset" (0) are dropped
                    db.execSQL("INSERT INTO api_autocrypt_peers " +
                            "(package_name, identifier, last_seen, gossip_last_seen_key, gossip_master_key_id, gossip_origin) " +
                            "SELECT package_name, identifier, last_updated, last_seen_key, master_key_id, 0 " +
                            "FROM tmp WHERE state = 1"); // Autocrypt 0.X, "gossip" -> now origin=autocrypt
                    db.execSQL("INSERT INTO api_autocrypt_peers " +
                            "(package_name, identifier, last_seen, gossip_last_seen_key, gossip_master_key_id, gossip_origin) " +
                            "SELECT package_name, identifier, last_updated, last_seen_key, master_key_id, 20 " +
                            "FROM tmp WHERE state = 2"); // "selected" keys -> now origin=dedup
                    db.execSQL("INSERT INTO api_autocrypt_peers " +
                            "(package_name, identifier, last_seen, last_seen_key, master_key_id, is_mutual) " +
                            "SELECT package_name, identifier, last_updated, last_seen_key, master_key_id, 0 " +
                            "FROM tmp WHERE state = 3"); // Autocrypt 0.X, state = "available"
                    db.execSQL("INSERT INTO api_autocrypt_peers " +
                            "(package_name, identifier, last_seen, last_seen_key, master_key_id, is_mutual) " +
                            "SELECT package_name, identifier, last_updated, last_seen_key, master_key_id, 1 " +
                            "FROM tmp WHERE state = 4"); // from Autocrypt 0.X, state = "mutual"
                    db.execSQL("DROP TABLE tmp");
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }

                db.execSQL("CREATE INDEX IF NOT EXISTS uids_by_email ON user_packets (email);");
                db.execSQL("DROP INDEX keys_by_rank");
                db.execSQL("CREATE INDEX keys_by_rank ON keys(rank, master_key_id);");
            }

            case 25: {
                try {
                    migrateSecretKeysFromDbToLocalStorage(db);
                } catch (IOException e) {
                    throw new IllegalStateException("Error migrating secret keys! This is bad!!");
                }
            }

            case 26: {
                migrateUpdatedKeysToKeyMetadataTable(db);
            }

            case 27: {
                renameApiAutocryptPeersTable(db);
            }
        }
    }

    private void migrateSecretKeysFromDbToLocalStorage(SupportSQLiteDatabase db) throws IOException {
        LocalSecretKeyStorage localSecretKeyStorage = LocalSecretKeyStorage.getInstance(context);
        Cursor cursor = db.query("SELECT master_key_id, key_ring_data FROM keyrings_secret");
        while (cursor.moveToNext()) {
            long masterKeyId = cursor.getLong(0);
            byte[] secretKeyBlob = cursor.getBlob(1);
            localSecretKeyStorage.writeSecretKey(masterKeyId, secretKeyBlob);
        }
        cursor.close();

        // we'll keep this around for now, but make sure to delete when migration looks ok!!
        // db.execSQL("DROP TABLE keyrings_secret");
    }

    private void migrateUpdatedKeysToKeyMetadataTable(SupportSQLiteDatabase db) {
        db.execSQL("ALTER TABLE updated_keys RENAME TO key_metadata;");
        db.execSQL("UPDATE key_metadata SET last_updated = last_updated * 1000;");
    }

    private void renameApiAutocryptPeersTable(SupportSQLiteDatabase db) {
        db.execSQL("ALTER TABLE api_autocrypt_peers RENAME TO autocrypt_peers;");
    }

    public void onDowngrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
        // Downgrade is ok for the debug version, makes it easier to work with branches
        if (Constants.DEBUG) {
            return;
        }
        // NOTE: downgrading the database is explicitly not allowed to prevent
        // someone from exploiting old bugs to export the database
        throw new RuntimeException("Downgrading the database is not allowed!");
    }

    private static void copy(File in, File out) throws IOException {
        FileInputStream is = new FileInputStream(in);
        FileOutputStream os = new FileOutputStream(out);
        try {
            byte[] buf = new byte[512];
            while (is.available() > 0) {
                int count = is.read(buf, 0, 512);
                os.write(buf, 0, count);
            }
        } finally {
            is.close();
            os.close();
        }
    }

    public static void debugBackup(Context context, boolean restore) throws IOException {
        if (!Constants.DEBUG) {
            return;
        }

        File in;
        File out;
        if (restore) {
            in = context.getDatabasePath("debug_backup.db");
            out = context.getDatabasePath(DATABASE_NAME);
        } else {
            in = context.getDatabasePath(DATABASE_NAME);
            out = context.getDatabasePath("debug_backup.db");
            // noinspection ResultOfMethodCallIgnored - this is a pure debug feature, anyways
            out.createNewFile();
        }
        if (!in.canRead()) {
            throw new IOException("Cannot read " +  in.getName());
        }
        if (!out.canWrite()) {
            throw new IOException("Cannot write " + out.getName());
        }
        copy(in, out);
    }

    // DANGEROUS, use in test code ONLY!
    public void clearDatabase() {
        getWritableDatabase().execSQL("delete from " + Tables.KEY_RINGS_PUBLIC);
        getWritableDatabase().execSQL("delete from " + Tables.API_ALLOWED_KEYS);
        getWritableDatabase().execSQL("delete from api_apps");
    }

}
