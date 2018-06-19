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

package org.sufficientlysecure.keychain.ui;


import java.io.IOException;
import java.util.List;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.arch.lifecycle.MediatorLiveData;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ViewAnimator;

import androidx.work.WorkStatus;
import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import eu.davidea.fastscroller.FastScroller;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.FlexibleAdapter.OnItemClickListener;
import eu.davidea.flexibleadapter.FlexibleAdapter.OnItemLongClickListener;
import eu.davidea.flexibleadapter.SelectableAdapter.Mode;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.compatibility.ClipboardReflection;
import org.sufficientlysecure.keychain.keysync.KeyserverSyncManager;
import org.sufficientlysecure.keychain.livedata.GenericLiveData;
import org.sufficientlysecure.keychain.livedata.KeyRingDao;
import org.sufficientlysecure.keychain.model.Key.UnifiedKeyInfo;
import org.sufficientlysecure.keychain.operations.results.BenchmarkResult;
import org.sufficientlysecure.keychain.operations.results.OperationResult;
import org.sufficientlysecure.keychain.pgp.PgpHelper;
import org.sufficientlysecure.keychain.provider.KeychainContract.KeyRings;
import org.sufficientlysecure.keychain.provider.KeychainDatabase;
import org.sufficientlysecure.keychain.service.BenchmarkInputParcel;
import org.sufficientlysecure.keychain.ui.adapter.FlexibleKeyHeader;
import org.sufficientlysecure.keychain.ui.adapter.FlexibleKeyItem;
import org.sufficientlysecure.keychain.ui.adapter.FlexibleKeyItemFactory;
import org.sufficientlysecure.keychain.ui.base.CryptoOperationHelper;
import org.sufficientlysecure.keychain.ui.base.RecyclerFragment;
import org.sufficientlysecure.keychain.ui.keyview.ViewKeyActivity;
import org.sufficientlysecure.keychain.ui.util.Notify;
import org.sufficientlysecure.keychain.ui.util.Notify.Style;
import org.sufficientlysecure.keychain.ui.util.recyclerview.DividerItemDecoration;
import org.sufficientlysecure.keychain.util.FabContainer;
import org.sufficientlysecure.keychain.util.Preferences;
import timber.log.Timber;


public class KeyListFragment extends RecyclerFragment<FlexibleAdapter<FlexibleKeyItem>>
        implements SearchView.OnQueryTextListener, OnItemClickListener, OnItemLongClickListener, FabContainer {

    static final int REQUEST_ACTION = 1;
    private static final int REQUEST_DELETE = 2;
    private static final int REQUEST_VIEW_KEY = 3;

    private ActionMode mActionMode = null;

    private Button vSearchButton;
    private ViewAnimator vSearchContainer;

    private FloatingActionsMenu mFab;

    private final ActionMode.Callback mActionCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.key_list_multi, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_key_list_multi_encrypt: {
                    long[] keyIds = getSelectedMasterKeyIds();
                    multiSelectEncrypt(keyIds);
                    mode.finish();
                    break;
                }

                case R.id.menu_key_list_multi_delete: {
                    long[] keyIds = getSelectedMasterKeyIds();
                    boolean hasSecret = isAnySecretKeySelected();
                    multiSelectDelete(keyIds, hasSecret);
                    mode.finish();
                    break;
                }
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
            if (getAdapter() != null) {
                getAdapter().clearSelection();
            }
        }
    };
    private FastScroller fastScroller;

    private void multiSelectDelete(long[] keyIds, boolean hasSecret) {
        Intent intent = new Intent(getActivity(), DeleteKeyDialogActivity.class);
        intent.putExtra(DeleteKeyDialogActivity.EXTRA_DELETE_MASTER_KEY_IDS, keyIds);
        intent.putExtra(DeleteKeyDialogActivity.EXTRA_HAS_SECRET, hasSecret);
        if (hasSecret) {
            intent.putExtra(DeleteKeyDialogActivity.EXTRA_KEYSERVER,
                    Preferences.getPreferences(getActivity()).getPreferredKeyserver());
        }
        startActivityForResult(intent, REQUEST_DELETE);
    }

    private void multiSelectEncrypt(long[] keyIds) {
        Intent intent = new Intent(getActivity(), EncryptFilesActivity.class);
        intent.setAction(EncryptFilesActivity.ACTION_ENCRYPT_DATA);
        intent.putExtra(EncryptFilesActivity.EXTRA_ENCRYPTION_KEY_IDS, keyIds);

        startActivityForResult(intent, REQUEST_ACTION);
    }

    private long[] getSelectedMasterKeyIds() {
        FlexibleAdapter<FlexibleKeyItem> adapter = getAdapter();
        List<Integer> selectedPositions = adapter.getSelectedPositions();
        long[] keyIds = new long[selectedPositions.size()];
        for (int i = 0; i < selectedPositions.size(); i++) {
            FlexibleKeyItem selectedItem = adapter.getItem(selectedPositions.get(i));
            if (selectedItem != null) {
                keyIds[i] = selectedItem.keyInfo.master_key_id();
            }
        }
        return keyIds;
    }

    private boolean isAnySecretKeySelected() {
        FlexibleAdapter<FlexibleKeyItem> adapter = getAdapter();
        for (int position : adapter.getSelectedPositions()) {
            FlexibleKeyItem item = adapter.getItem(position);
            if (item != null && item.keyInfo.has_any_secret()) {
                return true;
            }
        }
        return false;
    }

    public void startSafeSlingerForKey(long masterKeyId) {
        Intent safeSlingerIntent = new Intent(getActivity(), SafeSlingerActivity.class);
        safeSlingerIntent.putExtra(SafeSlingerActivity.EXTRA_MASTER_KEY_ID, masterKeyId);
        startActivityForResult(safeSlingerIntent, REQUEST_ACTION);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.key_list_fragment, container, false);

        mFab = view.findViewById(R.id.fab_main);

        FloatingActionButton fabQrCode = view.findViewById(R.id.fab_add_qr_code);
        FloatingActionButton fabCloud = view.findViewById(R.id.fab_add_cloud);
        FloatingActionButton fabFile = view.findViewById(R.id.fab_add_file);

        fabQrCode.setOnClickListener(v -> {
            mFab.collapse();
            scanQrCode();
        });
        fabCloud.setOnClickListener(v -> {
            mFab.collapse();
            searchCloud();
        });
        fabFile.setOnClickListener(v -> {
            mFab.collapse();
            importFile();
        });

        fastScroller = view.findViewById(R.id.fast_scroller);
        fastScroller.setBubbleTextCreator(this::getBubbleText);

        vSearchContainer = view.findViewById(R.id.search_container);
        vSearchButton = view.findViewById(R.id.search_button);
        vSearchButton.setOnClickListener(v -> startSearchForQuery());

        return view;
    }

    /**
     * Define Adapter and Loader on create of Activity
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // show app name instead of "keys" from nav drawer
        FragmentActivity activity = getActivity();
        if (activity == null) {
            throw new NullPointerException("Activity must be bound!");
        }
        activity.setTitle(R.string.app_name);

        // We have a menu item to show in action bar.
        setHasOptionsMenu(true);

        // Start out with a progress indicator.
        hideList(false);

        setLayoutManager(new LinearLayoutManager(getActivity()));
        getRecyclerView().addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL_LIST, false));

        KeyListViewModel keyListViewModel = ViewModelProviders.of(this).get(KeyListViewModel.class);
        keyListViewModel.getLiveData(getContext()).observe(this, this::onLoadKeyItems);
    }

    public static class KeyListViewModel extends ViewModel {
        MediatorLiveData<List<FlexibleKeyItem>> liveData;

        MediatorLiveData<List<FlexibleKeyItem>> getLiveData(Context context) {
            if (liveData == null) {
                KeyRingDao keyRingDao = KeyRingDao.getInstance(context);
                FlexibleKeyItemFactory flexibleKeyItemFactory = new FlexibleKeyItemFactory(context.getResources());
                GenericLiveData<List<UnifiedKeyInfo>> unifiedKeyInfoLiveData =
                        new GenericLiveData<>(context, KeyRings.CONTENT_URI, keyRingDao::getUnifiedKeyInfo);
                liveData = new MediatorLiveData<>();
                liveData.addSource(unifiedKeyInfoLiveData, flexibleKeyItemFactory::mapUnifiedKeyInfoToFlexibleKeyItems);
            }
            return liveData;
        }
    }

    private void onLoadKeyItems(List<FlexibleKeyItem> flexibleKeyItems) {
        FlexibleAdapter<FlexibleKeyItem> adapter = getAdapter();
        if (adapter == null) {
            adapter = new FlexibleAdapter<>(flexibleKeyItems);
            setAdapter(adapter);
            adapter.setDisplayHeadersAtStartUp(true);
            adapter.setStickyHeaders(true);
            adapter.addListener(this);
            adapter.setMode(Mode.MULTI);
            adapter.setFastScroller(fastScroller);
        } else {
            adapter.updateDataSet(flexibleKeyItems);
        }
    }

    private String getBubbleText(int position) {
        FlexibleKeyItem item = getAdapter().getItem(position);
        if (item == null) {
            return "";
        }
        FlexibleKeyHeader header = item.getHeader();
        return header.getSectionTitle();
    }

    @Override
    public void onStart() {
        super.onStart();

        checkClipboardForPublicKeyMaterial();
    }

    private void checkClipboardForPublicKeyMaterial() {
        CharSequence clipboardText = ClipboardReflection.getClipboardText(getActivity());

        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                if (clipboardText == null) {
                    return false;
                }

                // see if it looks like a pgp thing
                String publicKeyContent = PgpHelper.getPgpPublicKeyContent(clipboardText);

                return publicKeyContent != null;
            }

            @Override
            protected void onPostExecute(Boolean clipboardDataFound) {
                super.onPostExecute(clipboardDataFound);

                if (clipboardDataFound) {
                    showClipboardDataSnackbar();
                }
            }
        }.execute();
    }

    private void showClipboardDataSnackbar() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        Notify.create(activity, R.string.snack_keylist_clipboard_title, Notify.LENGTH_INDEFINITE, Style.OK,
                () -> {
                    Intent intentImportExisting = new Intent(getActivity(), ImportKeysActivity.class);
                    intentImportExisting.setAction(ImportKeysActivity.ACTION_IMPORT_KEY_FROM_CLIPBOARD);
                    startActivity(intentImportExisting);
                }, R.string.snack_keylist_clipboard_action).show(this);
    }

    private void startSearchForQuery() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        Intent searchIntent = new Intent(activity, ImportKeysActivity.class);
        searchIntent.putExtra(ImportKeysActivity.EXTRA_QUERY, getAdapter().getFilter(String.class));
        searchIntent.setAction(ImportKeysActivity.ACTION_IMPORT_KEY_FROM_KEYSERVER);
        startActivity(searchIntent);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.key_list, menu);

        if (Constants.DEBUG) {
            menu.findItem(R.id.menu_key_list_debug_bench).setVisible(true);
            menu.findItem(R.id.menu_key_list_debug_read).setVisible(true);
            menu.findItem(R.id.menu_key_list_debug_write).setVisible(true);
            menu.findItem(R.id.menu_key_list_debug_first_time).setVisible(true);
            menu.findItem(R.id.menu_key_list_debug_bgsync).setVisible(true);
        }

        // Get the searchview
        MenuItem searchItem = menu.findItem(R.id.menu_key_list_search);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);

        // Execute this when searching
        searchView.setOnQueryTextListener(this);

        // Erase search result without focus
        MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                getAdapter().setFilter(null);
                getAdapter().filterItems();
                return true;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onItemClick(View view, int position) {
        FlexibleKeyItem item = getAdapter().getItem(position);
        if (item == null) {
            return false;
        }

        if (mActionMode != null && position != RecyclerView.NO_POSITION) {
            toggleSelection(position);
            return true;
        }

        long masterKeyId = item.keyInfo.master_key_id();
        Intent viewIntent = new Intent(getActivity(), ViewKeyActivity.class);
        viewIntent.setData(KeyRings.buildGenericKeyRingUri(masterKeyId));
        startActivityForResult(viewIntent, REQUEST_VIEW_KEY);
        return false;
    }

    @Override
    public void onItemLongClick(int position) {
        if (mActionMode == null) {
            FragmentActivity activity = getActivity();
            if (activity != null) {
                mActionMode = activity.startActionMode(mActionCallback);
            }
        }
        toggleSelection(position);
    }

    private void toggleSelection(int position) {
        getAdapter().toggleSelection(position);

        int count = getAdapter().getSelectedItemCount();

        if (count == 0) {
            mActionMode.finish();
        } else {
            setContextTitle(count);
        }
    }

    private void setContextTitle(int selectedCount) {
        String keysSelected = getResources().getQuantityString(
                R.plurals.key_list_selected_keys, selectedCount, selectedCount);
        mActionMode.setTitle(keysSelected);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_key_list_create: {
                createKey();
                return true;
            }
            case R.id.menu_key_list_update_all_keys: {
                updateAllKeys();
                return true;
            }
            case R.id.menu_key_list_debug_read: {
                try {
                    KeychainDatabase.debugBackup(getActivity(), true);
                    Notify.create(getActivity(), "Restored debug_backup.db", Notify.Style.OK).show();
                    getActivity().getContentResolver().notifyChange(KeyRings.CONTENT_URI, null);
                } catch (IOException e) {
                    Timber.e(e, "IO Error");
                    Notify.create(getActivity(), "IO Error " + e.getMessage(), Notify.Style.ERROR).show();
                }
                return true;
            }
            case R.id.menu_key_list_debug_write: {
                try {
                    KeychainDatabase.debugBackup(getActivity(), false);
                    Notify.create(getActivity(), "Backup to debug_backup.db completed", Notify.Style.OK).show();
                } catch (IOException e) {
                    Timber.e(e, "IO Error");
                    Notify.create(getActivity(), "IO Error: " + e.getMessage(), Notify.Style.ERROR).show();
                }
                return true;
            }
            case R.id.menu_key_list_debug_first_time: {
                Preferences prefs = Preferences.getPreferences(getActivity());
                prefs.setFirstTime(true);
                Intent intent = new Intent(getActivity(), CreateKeyActivity.class);
                intent.putExtra(CreateKeyActivity.EXTRA_FIRST_TIME, true);
                startActivity(intent);
                getActivity().finish();
                return true;
            }
            case R.id.menu_key_list_debug_bgsync: {
                KeyserverSyncManager.runSyncNow(false, false);
                return true;
            }
            case R.id.menu_key_list_debug_bench: {
                benchmark();
                return true;
            }
            default: {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        return true;
    }

    @Override
    public boolean onQueryTextChange(String searchText) {
        getAdapter().setFilter(searchText);
        getAdapter().filterItems(300);

        if (searchText.length() > 2) {
            vSearchButton.setText(getString(R.string.btn_search_for_query, searchText));
            vSearchContainer.setDisplayedChild(1);
            vSearchContainer.setVisibility(View.VISIBLE);
        } else {
            vSearchContainer.setDisplayedChild(0);
            vSearchContainer.setVisibility(View.GONE);
        }

        return true;
    }

    private void searchCloud() {
        Intent importIntent = new Intent(getActivity(), ImportKeysActivity.class);
        importIntent.putExtra(ImportKeysActivity.EXTRA_QUERY, (String) null); // hack to show only cloud tab
        startActivity(importIntent);
    }

    private void scanQrCode() {
        Intent scanQrCode = new Intent(getActivity(), ImportKeysProxyActivity.class);
        scanQrCode.setAction(ImportKeysProxyActivity.ACTION_SCAN_IMPORT);
        startActivityForResult(scanQrCode, REQUEST_ACTION);
    }

    private void importFile() {
        Intent intentImportExisting = new Intent(getActivity(), ImportKeysActivity.class);
        intentImportExisting.setAction(ImportKeysActivity.ACTION_IMPORT_KEY_FROM_FILE_AND_RETURN);
        startActivityForResult(intentImportExisting, REQUEST_ACTION);
    }

    private void createKey() {
        Intent intent = new Intent(getActivity(), CreateKeyActivity.class);
        startActivityForResult(intent, REQUEST_ACTION);
    }

    private void updateAllKeys() {
        KeyserverSyncManager.getSyncWorkerLiveData().observe(this, this::onSyncWorkerUpdate);
        KeyserverSyncManager.runSyncNow(true, true);
    }

    private void onSyncWorkerUpdate(List<WorkStatus> workStatuses) {
        if (workStatuses == null || workStatuses.isEmpty()) {
            return;
        }

        WorkStatus workStatus = workStatuses.get(0);
        switch (workStatus.getState()) {
            case RUNNING:
                Notify.create(getActivity(), R.string.snack_keysync_start, Style.OK).show(this);
                break;
            case SUCCEEDED:
                Notify.create(getActivity(), R.string.snack_keysync_finished, Style.OK).show(this);
                break;
            case FAILED:
                Notify.create(getActivity(), R.string.snack_keysync_error, Style.ERROR).show(this);
                break;
        }
    }

    private void benchmark() {
        CryptoOperationHelper.Callback<BenchmarkInputParcel, BenchmarkResult> callback
                = new CryptoOperationHelper.Callback<BenchmarkInputParcel, BenchmarkResult>() {

            @Override
            public BenchmarkInputParcel createOperationInput() {
                return BenchmarkInputParcel.newInstance(); // we want to perform a full consolidate
            }

            @Override
            public void onCryptoOperationSuccess(BenchmarkResult result) {
                result.createNotify(getActivity()).show();
            }

            @Override
            public void onCryptoOperationCancelled() {
            }

            @Override
            public void onCryptoOperationError(BenchmarkResult result) {
                result.createNotify(getActivity()).show();
            }

            @Override
            public boolean onCryptoSetProgress(String msg, int progress, int max) {
                return false;
            }
        };

        CryptoOperationHelper opHelper = new CryptoOperationHelper<>(2, this, callback, R.string.progress_importing);
        opHelper.cryptoOperation();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_DELETE: {
                if (mActionMode != null) {
                    mActionMode.finish();
                }

                if (data != null && data.hasExtra(OperationResult.EXTRA_RESULT)) {
                    OperationResult result = data.getParcelableExtra(OperationResult.EXTRA_RESULT);
                    result.createNotify(getActivity()).show();
                } else {
                    super.onActivityResult(requestCode, resultCode, data);
                }
                break;
            }
            case REQUEST_ACTION: {
                // if a result has been returned, display a notify
                if (data != null && data.hasExtra(OperationResult.EXTRA_RESULT)) {
                    OperationResult result = data.getParcelableExtra(OperationResult.EXTRA_RESULT);
                    result.createNotify(getActivity()).show();
                } else {
                    super.onActivityResult(requestCode, resultCode, data);
                }
                break;
            }
            case REQUEST_VIEW_KEY: {
                if (data != null && data.hasExtra(OperationResult.EXTRA_RESULT)) {
                    OperationResult result = data.getParcelableExtra(OperationResult.EXTRA_RESULT);
                    result.createNotify(getActivity()).show();
                } else {
                    super.onActivityResult(requestCode, resultCode, data);
                }
                break;
            }
        }
    }

    @Override
    public void fabMoveUp(int height) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(mFab, "translationY", 0, -height);
        // we're a little behind, so skip 1/10 of the time
        anim.setDuration(270);
        anim.start();
    }

    @Override
    public void fabRestorePosition() {
        ObjectAnimator anim = ObjectAnimator.ofFloat(mFab, "translationY", 0);
        // we're a little ahead, so wait a few ms
        anim.setStartDelay(70);
        anim.setDuration(300);
        anim.start();
    }

}
