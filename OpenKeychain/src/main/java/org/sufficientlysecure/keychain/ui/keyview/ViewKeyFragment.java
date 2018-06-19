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

package org.sufficientlysecure.keychain.ui.keyview;


import java.util.List;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.PopupMenu.OnDismissListener;
import android.support.v7.widget.PopupMenu.OnMenuItemClickListener;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.compatibility.DialogFragmentWorkaround;
import org.sufficientlysecure.keychain.model.KeyMetadata;
import org.sufficientlysecure.keychain.operations.results.OperationResult;
import org.sufficientlysecure.keychain.ui.base.LoaderFragment;
import org.sufficientlysecure.keychain.ui.keyview.loader.IdentityDao.IdentityInfo;
import org.sufficientlysecure.keychain.ui.keyview.loader.SubkeyStatusDao.KeySubkeyStatus;
import org.sufficientlysecure.keychain.ui.keyview.loader.SystemContactDao.SystemContactInfo;
import org.sufficientlysecure.keychain.ui.keyview.presenter.IdentitiesPresenter;
import org.sufficientlysecure.keychain.ui.keyview.presenter.KeyHealthPresenter;
import org.sufficientlysecure.keychain.ui.keyview.presenter.KeyserverStatusPresenter;
import org.sufficientlysecure.keychain.ui.keyview.presenter.SystemContactPresenter;
import org.sufficientlysecure.keychain.ui.keyview.presenter.ViewKeyMvpView;
import org.sufficientlysecure.keychain.ui.keyview.view.IdentitiesCardView;
import org.sufficientlysecure.keychain.ui.keyview.view.KeyHealthView;
import org.sufficientlysecure.keychain.ui.keyview.view.KeyserverStatusView;
import org.sufficientlysecure.keychain.ui.keyview.view.SystemContactCardView;


public class ViewKeyFragment extends LoaderFragment implements ViewKeyMvpView, OnMenuItemClickListener {
    public static final String ARG_MASTER_KEY_ID = "master_key_id";
    public static final String ARG_IS_SECRET = "is_secret";

    boolean mIsSecret = false;

    private IdentitiesCardView identitiesCardView;
    private IdentitiesPresenter identitiesPresenter;

    SystemContactCardView systemContactCard;
    SystemContactPresenter systemContactPresenter;

    KeyHealthView keyStatusHealth;
    KeyserverStatusView keyStatusKeyserver;

    KeyHealthPresenter keyHealthPresenter;
    KeyserverStatusPresenter keyserverStatusPresenter;

    private Integer displayedContextMenuPosition;

    /**
     * Creates new instance of this fragment
     */
    public static ViewKeyFragment newInstance(long masterKeyId, boolean isSecret) {
        ViewKeyFragment frag = new ViewKeyFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_MASTER_KEY_ID, masterKeyId);
        args.putBoolean(ARG_IS_SECRET, isSecret);

        frag.setArguments(args);

        return frag;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup superContainer, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, superContainer, savedInstanceState);
        View view = inflater.inflate(R.layout.view_key_fragment, getContainer());

        identitiesCardView = view.findViewById(R.id.card_identities);

        systemContactCard = view.findViewById(R.id.linked_system_contact_card);
        keyStatusHealth = view.findViewById(R.id.key_status_health);
        keyStatusKeyserver = view.findViewById(R.id.key_status_keyserver);

        return root;
    }

    public static class KeyFragmentViewModel extends ViewModel {
        private LiveData<List<IdentityInfo>> identityInfo;
        private LiveData<KeySubkeyStatus> subkeyStatus;
        private LiveData<SystemContactInfo> systemContactInfo;
        private LiveData<KeyMetadata> keyserverStatus;

        LiveData<List<IdentityInfo>> getIdentityInfo(IdentitiesPresenter identitiesPresenter) {
            if (identityInfo == null) {
                identityInfo = identitiesPresenter.getLiveDataInstance();
            }
            return identityInfo;
        }

        LiveData<KeySubkeyStatus> getSubkeyStatus(KeyHealthPresenter keyHealthPresenter) {
            if (subkeyStatus == null) {
                subkeyStatus = keyHealthPresenter.getLiveDataInstance();
            }
            return subkeyStatus;
        }

        LiveData<SystemContactInfo> getSystemContactInfo(SystemContactPresenter systemContactPresenter) {
            if (systemContactInfo == null) {
                systemContactInfo = systemContactPresenter.getLiveDataInstance();
            }
            return systemContactInfo;
        }

        LiveData<KeyMetadata> getKeyserverStatus(KeyserverStatusPresenter keyserverStatusPresenter) {
            if (keyserverStatus == null) {
                keyserverStatus = keyserverStatusPresenter.getLiveDataInstance();
            }
            return keyserverStatus;
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        long masterKeyId = getArguments().getLong(ARG_MASTER_KEY_ID);
        mIsSecret = getArguments().getBoolean(ARG_IS_SECRET);

        KeyFragmentViewModel model = ViewModelProviders.of(this).get(KeyFragmentViewModel.class);

        identitiesPresenter = new IdentitiesPresenter(
                getContext(), identitiesCardView, this, masterKeyId, mIsSecret);
        model.getIdentityInfo(identitiesPresenter).observe(this, identitiesPresenter);

        systemContactPresenter = new SystemContactPresenter(
                getContext(), systemContactCard, masterKeyId, mIsSecret);
        model.getSystemContactInfo(systemContactPresenter).observe(this, systemContactPresenter);

        keyHealthPresenter = new KeyHealthPresenter(getContext(), keyStatusHealth, masterKeyId);
        model.getSubkeyStatus(keyHealthPresenter).observe(this, keyHealthPresenter);

        keyserverStatusPresenter = new KeyserverStatusPresenter(
                getContext(), keyStatusKeyserver, masterKeyId, mIsSecret);
        model.getKeyserverStatus(keyserverStatusPresenter).observe(this, keyserverStatusPresenter);
    }

    @Override
    public void switchToFragment(final Fragment frag, final String backStackName) {
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                getFragmentManager().beginTransaction()
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                        .replace(R.id.view_key_fragment, frag)
                        .addToBackStack(backStackName)
                        .commit();
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // if a result has been returned, display a notify
        if (data != null && data.hasExtra(OperationResult.EXTRA_RESULT)) {
            OperationResult result = data.getParcelableExtra(OperationResult.EXTRA_RESULT);
            result.createNotify(getActivity()).show();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public boolean isValidForData(boolean isSecret) {
        return isSecret == mIsSecret;
    }

    @Override
    public void startActivityAndShowResultSnackbar(Intent intent) {
        startActivityForResult(intent, 0);
    }

    @Override
    public void showDialogFragment(final DialogFragment dialogFragment, final String tag) {
        DialogFragmentWorkaround.INTERFACE.runnableRunDelayed(new Runnable() {
            public void run() {
                dialogFragment.show(getActivity().getSupportFragmentManager(), tag);
            }
        });
    }

    @Override
    public void showContextMenu(int position, View anchor) {
        displayedContextMenuPosition = position;

        PopupMenu menu = new PopupMenu(getContext(), anchor);
        menu.inflate(R.menu.identity_context_menu);
        menu.setOnMenuItemClickListener(this);
        menu.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(PopupMenu popupMenu) {
                displayedContextMenuPosition = null;
            }
        });
        menu.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (displayedContextMenuPosition == null) {
            return false;
        }

        switch (item.getItemId()) {
            case R.id.autocrypt_forget:
                int position = displayedContextMenuPosition;
                displayedContextMenuPosition = null;
                identitiesPresenter.onClickForgetIdentity(position);
                return true;
        }

        return false;
    }
}
