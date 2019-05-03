package com.gigya.android.sdk.containers;

import android.os.Build;

import com.gigya.android.sdk.Config;
import com.gigya.android.sdk.Gigya;
import com.gigya.android.sdk.account.AccountService;
import com.gigya.android.sdk.account.IAccountService;
import com.gigya.android.sdk.api.ApiService;
import com.gigya.android.sdk.api.BusinessApiService;
import com.gigya.android.sdk.api.IApiService;
import com.gigya.android.sdk.api.IBusinessApiService;
import com.gigya.android.sdk.encryption.ISecureKey;
import com.gigya.android.sdk.encryption.SessionKey;
import com.gigya.android.sdk.encryption.SessionKeyLegacy;
import com.gigya.android.sdk.interruption.IInterruptionsHandler;
import com.gigya.android.sdk.interruption.InterruptionHandler;
import com.gigya.android.sdk.network.adapter.IRestAdapter;
import com.gigya.android.sdk.network.adapter.RestAdapter;
import com.gigya.android.sdk.persistence.IPersistenceService;
import com.gigya.android.sdk.persistence.PersistenceService;
import com.gigya.android.sdk.providers.IProviderFactory;
import com.gigya.android.sdk.providers.ProviderFactory;
import com.gigya.android.sdk.session.ISessionService;
import com.gigya.android.sdk.session.ISessionVerificationService;
import com.gigya.android.sdk.session.SessionService;
import com.gigya.android.sdk.session.SessionVerificationService;
import com.gigya.android.sdk.ui.IPresenter;
import com.gigya.android.sdk.ui.Presenter;
import com.gigya.android.sdk.ui.plugin.IWebBridgeFactory;
import com.gigya.android.sdk.ui.plugin.IWebViewFragmentFactory;
import com.gigya.android.sdk.ui.plugin.WebBridgeFactory;
import com.gigya.android.sdk.ui.plugin.WebViewFragmentFactory;

public class GigyaContainer extends IoCContainer {
    public GigyaContainer() {
        bind(Gigya.class, Gigya.class, true);
        bind(Config.class, Config.class, true); // Concrete.
        bind(IRestAdapter.class, RestAdapter.class, true);
        bind(IApiService.class, ApiService.class, false);
        bind(ISecureKey.class, Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 ? SessionKey.class
                : SessionKeyLegacy.class, true);
        bind(IPersistenceService.class, PersistenceService.class, false);
        bind(ISessionService.class, SessionService.class, true);
        bind(IAccountService.class, AccountService.class, true);
        bind(ISessionVerificationService.class, SessionVerificationService.class, true);
        bind(IProviderFactory.class, ProviderFactory.class, false);
        bind(IBusinessApiService.class, BusinessApiService.class, true);
        bind(IWebBridgeFactory.class, WebBridgeFactory.class, false);
        bind(IWebViewFragmentFactory.class, WebViewFragmentFactory.class, false);
        bind(IPresenter.class, Presenter.class, false);
        bind(IInterruptionsHandler.class, InterruptionHandler.class, true);
    }
}
