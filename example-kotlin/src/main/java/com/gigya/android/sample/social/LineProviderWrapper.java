package com.gigya.android.sample.social;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.gigya.android.sdk.providers.external.IProviderWrapper;
import com.gigya.android.sdk.providers.external.IProviderWrapperCallback;
import com.gigya.android.sdk.ui.HostActivity;
import com.linecorp.linesdk.LineApiResponse;
import com.linecorp.linesdk.Scope;
import com.linecorp.linesdk.api.LineApiClient;
import com.linecorp.linesdk.api.LineApiClientBuilder;
import com.linecorp.linesdk.auth.LineAuthenticationParams;
import com.linecorp.linesdk.auth.LineLoginApi;
import com.linecorp.linesdk.auth.LineLoginResult;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class LineProviderWrapper implements IProviderWrapper {

    private String lineChannelID = "";

    private static final int REQUEST_CODE = 1;

    IProviderWrapperCallback providerWrapperCallback;
    final Context context;

    LineProviderWrapper(Context context) {
        this.context = context;
    }

    @Nullable
    private String channelIdFromMetaData() {
        String clientId = null;
        try {
            ApplicationInfo appInfo = this.context.getPackageManager().getApplicationInfo(this.context.getPackageName(), PackageManager.GET_META_DATA);
            Bundle metaData = appInfo.metaData;
            if (metaData.get("lineChannelID") instanceof String) {
                clientId = (String) metaData.get("lineChannelID");
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return clientId;
    }

    @Override
    public void login(Context context, Map<String, Object> params, IProviderWrapperCallback callback) {
        providerWrapperCallback = callback;
        HostActivity.present(context, new HostActivity.HostActivityLifecycleCallbacks() {
            @Override
            public void onCreate(AppCompatActivity activity, @Nullable Bundle savedInstanceState) {
                // Fetch channel Id from meta-data.
                lineChannelID = channelIdFromMetaData();

                if (lineChannelID == null) {
                    // Fail login.
                    callback.onFailed("Channel Id not available");
                    activity.finish();
                    return;
                }

                Intent loginIntent = LineLoginApi.getLoginIntent(
                        activity,
                        lineChannelID,
                        new LineAuthenticationParams.Builder()
                                .scopes(Arrays.asList(Scope.PROFILE))
                                .build());
                activity.startActivityForResult(loginIntent, REQUEST_CODE);
            }

            @Override
            public void onActivityResult(AppCompatActivity activity, int requestCode, int resultCode, @Nullable Intent data) {
                if (providerWrapperCallback == null) {
                    return;
                }
                if (requestCode == REQUEST_CODE) {
                    LineLoginResult result = LineLoginApi.getLoginResultFromIntent(data);
                    switch (result.getResponseCode()) {
                        case SUCCESS:
                            if (result.getLineCredential() == null) {
                                // Fail login.
                                return;
                            }
                            final String accessToken = result.getLineCredential().getAccessToken().getTokenString();
                            final Map<String, Object> loginMap = new HashMap<>();
                            loginMap.put("token", accessToken);
                            providerWrapperCallback.onLogin(loginMap);
                            break;
                        case CANCEL:
                            providerWrapperCallback.onCanceled();
                            break;
                        default:
                            // Any other is an error.
                            providerWrapperCallback.onFailed(result.getErrorData().getMessage());
                            break;
                    }
                    activity.finish();
                }
            }
        });
    }

    @Override
    public void logout() {
        LineApiClientBuilder builder = new LineApiClientBuilder(context, lineChannelID);
        LineApiClient client = builder.build();
        new LogoutTask(client).execute();
    }

    private static class LogoutTask extends AsyncTask<Void, Void, LineApiResponse> {

        private LineApiClient _client;

        LogoutTask(LineApiClient client) {
            _client = client;
        }

        @Override
        protected LineApiResponse doInBackground(Void... voids) {
            return _client.logout();
        }

        @Override
        protected void onPostExecute(LineApiResponse lineApiResponse) {
            if (lineApiResponse.isSuccess()) {
                /* Logout success. */
            } else {
                /* Logout error. */
            }
            _client = null;
        }
    }
}
