package com.gigya.android.sdk.ui.plugin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.gigya.android.sdk.GigyaPluginCallback;
import com.gigya.android.sdk.log.GigyaLogger;
import com.gigya.android.sdk.network.GigyaError;
import com.gigya.android.sdk.ui.HostActivity;
import com.gigya.android.sdk.ui.WebBridge;
import com.gigya.android.sdk.ui.WebViewFragment;
import com.gigya.android.sdk.utils.ObjectUtils;
import com.gigya.android.sdk.utils.UiUtils;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static android.app.Activity.RESULT_OK;

public class PluginFragment<T> extends WebViewFragment implements HostActivity.OnBackPressListener {

    /* Plugin variants. */
    public static final String PLUGIN_SCREENSETS = "accounts.screenSet";
    public static final String PLUGIN_COMMENTS = "comments.commentsUI";

    private static final String LOG_TAG = "PluginFragment";

    /* Arguments. */
    public static final String ARG_API_KEY = "arg_api_key";
    public static final String ARG_API_DOMAIN = "arg_api_domain";
    public static final String ARG_OBFUSCATE = "arg_obfuscate";
    public static final String ARG_PLUGIN = "arg_plugin";
    public static final String ARG_PARAMS = "arg_params";

    /* Private descriptors. */
    private static final String REDIRECT_URL_SCHEME = "gsapi";
    private static final String ON_JS_LOAD_ERROR = "on_js_load_error";
    private static final String ON_JS_EXCEPTION = "on_js_exception";
    private static final String CONTAINER_ID = "pluginContainer";
    private static final int JS_TIMEOUT = 10000;

    public static void present(AppCompatActivity activity, Bundle args, @NonNull GigyaPluginCallback pluginCallbacks) {
        PluginFragment fragment = new PluginFragment();
        fragment.setArguments(args);
        fragment._pluginCallbacks = pluginCallbacks;
        FragmentTransaction fragmentTransaction = activity.getSupportFragmentManager().beginTransaction();
        fragmentTransaction.add(fragment, LOG_TAG);
        fragmentTransaction.commitAllowingStateLoss();
    }

    private String _apiKey, _apiDomain, _plugin;
    private HashMap<String, Object> _params;

    private boolean _fullScreen;

    private boolean _obfuscate;

    private WebBridge _webBridge;

    private GigyaPluginCallback<T> _pluginCallbacks;

    @Override
    protected boolean wrapContent() {
        return false;
    }

    //region lifecycle

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (_fullScreen) {
            setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        }
    }

    @Override
    public void onDestroyView() {
        if (_imagePathCallback != null) {
            _imagePathCallback = null;
        }
        super.onDestroyView();
    }

    @Override

    public boolean onBackPressed() {
        if (_webView.canGoBack()) {
            _webView.goBack();
            return true;
        }
        return false;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof HostActivity) {
            ((HostActivity) context).addBackPressListener(this);
        }
    }

    @Override
    public void onDetach() {
        final HostActivity hostActivity = (HostActivity) getActivity();
        if (hostActivity != null) {
            hostActivity.removeBackPressListener(this);
        }
        super.onDetach();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        _pluginCallbacks.onCancel();
        if (getActivity() != null) {
            getActivity().onBackPressed();
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.post(new Runnable() {
            @Override
            public void run() {
                final String html = getHTML();
                _webView.loadDataWithBaseURL("http://www.gigya.com", html, "text/html", "utf-8", null);
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void parseArguments() {
        if (getArguments() == null) {
            return;
        }
        Bundle args = getArguments();
        _apiKey = args.getString(ARG_API_KEY);
        _apiDomain = args.getString(ARG_API_DOMAIN);
        _obfuscate = args.getBoolean(ARG_OBFUSCATE);
        _plugin = args.getString(ARG_PLUGIN);
        _params = (HashMap<String, Object>) args.getSerializable(ARG_PARAMS);

        if (_params != null) {
            _fullScreen = (boolean) ObjectUtils.firstNonNull(_params.get(GigyaPluginPresenter.SHOW_FULL_SCREEN), false);
        }

        if (_apiKey == null || _plugin == null) {
            /* Implementation error. */
            dismiss();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        GigyaLogger.debug(LOG_TAG, "onRequestPermissionsResult:");
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                GigyaLogger.debug(LOG_TAG, "External storage permission explicitly granted.");
                if (_imagePathCallback != null) {
                    sendImageChooserIntent();
                }
            } else {
                // Permission denied by the user.
                GigyaLogger.debug(LOG_TAG, "External storage permission explicitly denied.");
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (requestCode != FILE_CHOOSER_MEDIA_REQUEST_CODE || _imagePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }
            Uri[] results = null;
            if (resultCode == RESULT_OK) {
                if (data != null && data.getDataString() != null) {
                    String dataString = data.getDataString();
                    results = new Uri[]{Uri.parse(dataString)};
                }
                // If there is not data, then we may have taken a photo
                else if (_cameraTempImagePath != null) {
                    results = new Uri[]{Uri.parse(_cameraTempImagePath)};
                }
            }
            _imagePathCallback.onReceiveValue(results);
            _imagePathCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    //endregion

    //region WebView setup

    @Override
    protected void setUpWebView() {
        super.setUpWebView();

        _webView.getSettings().setLoadWithOverviewMode(true);
        _webView.getSettings().setUseWideViewPort(true);

        _webView.setWebViewClient(new WebViewClient() {

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                overrideUrlLoad(uri);
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String urlString) {
                Uri uri = Uri.parse(urlString);
                overrideUrlLoad(uri);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                _progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // TODO: 27/01/2019 Error. 
            }

            private void overrideUrlLoad(Uri uri) {
                if (ObjectUtils.safeEquals(uri.getScheme(), REDIRECT_URL_SCHEME) && ObjectUtils.safeEquals(uri.getHost(), ON_JS_LOAD_ERROR)) {
                    _pluginCallbacks.onError(GigyaError.generalError());
                } else if (ObjectUtils.safeEquals(uri.getScheme(), REDIRECT_URL_SCHEME) && ObjectUtils.safeEquals(uri.getHost(), ON_JS_EXCEPTION)) {
                    _pluginCallbacks.onError(GigyaError.generalError());
                } else if (!_webBridge.handleUrl(uri.toString())) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(browserIntent);
                }
            }
        });

        setUpFileChooserInteraction();
        setupWebBridge();
    }

    //endregion

    //region WebBridge

    private void setupWebBridge() {
        _webBridge = new WebBridge(_obfuscate, new WebBridge.WebBridgeInteractions<T>() {
            @Override
            public void onPluginEvent(Map<String, Object> event, String containerID) {
                if (containerID.equals(CONTAINER_ID)) {
                    final String eventName = (String) ObjectUtils.firstNonNull(event.get("eventName"), "");
                    if (eventName.equals("load")) {
                        _progressBar.setVisibility(View.INVISIBLE);
                    }

                    if (eventName.equals("hide") || eventName.equals("close")) {
                        dismiss();
                        return;
                    }

                    _pluginCallbacks.onEvent(eventName, event);
                }
            }

            @Override
            public void onAuthEvent(T obj) {
                _pluginCallbacks.onSuccess(obj);
            }

            @Override
            public void onCancel() {
                _pluginCallbacks.onCancel();
            }

            @Override
            public void onError(GigyaError error) {
                _pluginCallbacks.onError(error);
            }
        });
        _webBridge.attach(_webView);
    }

    //endregion

    //region File chooser

    private static final int PERMISSION_REQUEST_CODE = 14;
    private static final int FILE_CHOOSER_MEDIA_REQUEST_CODE = 15;
    private String _cameraTempImagePath;
    private ValueCallback<Uri[]> _imagePathCallback;

    private void setUpFileChooserInteraction() {
        _webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                // TODO: 05/02/2019 Verify flow for pre M devices!
                if (_imagePathCallback != null) {
                    _imagePathCallback.onReceiveValue(null);
                }
                // TODO: 05/02/2019 Nullify it onDestroy()
                _imagePathCallback = filePathCallback;

                GigyaLogger.debug(LOG_TAG, "onShowFileChooser: ");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (getActivity() == null) {
                        return false;
                    }
                    final int externalStoragePermission = ContextCompat.checkSelfPermission(webView.getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    if (externalStoragePermission != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
                    } else {
                        GigyaLogger.debug(LOG_TAG, "External storage permission implicitly granted.");
                        sendImageChooserIntent();
                    }
                }
                return true;
            }
        });
    }

    private void sendImageChooserIntent() {
        if (getActivity() == null) {
            return;
        }
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            File imageFile = null;
            try {
                imageFile = createImageFile();
                takePictureIntent.putExtra("imagePath", _cameraTempImagePath);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            if (imageFile != null) {
                _cameraTempImagePath = "file:" + imageFile.getAbsolutePath();
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(imageFile));
            } else {
                takePictureIntent = null;
            }
        }

        final Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
        contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
        contentSelectionIntent.setType("image/*");

        final Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
        chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
        chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser");
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, takePictureIntent != null ?
                new Intent[]{takePictureIntent} : new Intent[0]);
        startActivityForResult(chooserIntent, FILE_CHOOSER_MEDIA_REQUEST_CODE);
    }

    @SuppressLint("SimpleDateFormat")
    private File createImageFile() throws IOException {
        final String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        final String imageFileName = "JPEG_" + timeStamp + "_";
        final File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        return File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
    }

    //endregion

    //region HTML & parameters

    private String getHTML() {
        organizeParameters();
        String flattenedParams = new JSONObject(_params).toString();
        final String template =
                "<head>" +
                        "<meta name='viewport' content='initial-scale=1,maximum-scale=1,user-scalable=no' />" +
                        "<script>" +
                        "function onJSException(ex) {" +
                        "document.location.href = '%s://%s?ex=' + encodeURIComponent(ex);" +
                        "}" +
                        "function onJSLoad() {" +
                        "if (gigya && gigya.isGigya)" +
                        "window.__wasSocializeLoaded = true;" +
                        "}" +
                        "setTimeout(function() {" +
                        "if (!window.__wasSocializeLoaded)" +
                        "document.location.href = '%s://%s';" +
                        "}, %s);" +
                        "</script>" +
                        "<script src='https://cdns." + _apiDomain + "/JS/gigya.js?apikey=%s' type='text/javascript' onLoad='onJSLoad();'>" +
                        "{" +
                        "deviceType: 'mobile'" +
                        "}" +
                        "</script>" +
                        "</head>" +
                        "<body>" +
                        "<div id='%s'></div>" +
                        "<script>" +
                        "%s" +
                        "try {" +
                        "gigya._.apiAdapters.mobile.showPlugin('%s', %s);" +
                        "} catch (ex) { onJSException(ex); }" +
                        "</script>" +
                        "</body>";
        return String.format(template, REDIRECT_URL_SCHEME, ON_JS_EXCEPTION, REDIRECT_URL_SCHEME, ON_JS_LOAD_ERROR, JS_TIMEOUT, _apiKey, CONTAINER_ID, "", _plugin, flattenedParams);
    }

    private void organizeParameters() {
        _params.put("containerID", CONTAINER_ID);
        if (_params.containsKey("commentsUI")) {
            _params.put("hideShareButtons", true);
            if (_params.get("version") != null && (int) _params.get("version") == -1) {
                _params.put("version", 2);
            }
        }
        if (_params.containsKey("RatingUI") && _params.get("showCommentButton") == null) {
            _params.put("showCommentButton", false);
        }

        if (getView() != null) {
            int width = getView().getWidth();
            if (width == 0) {
                width = (int) (UiUtils.getScreenSize((Activity) getView().getContext()).first * 0.9);
            }
            _params.put("width", UiUtils.pixelsToDp(width, getView().getContext()));
        }
        // TODO: 27/01/2019 Add disabled providers...
    }

    //endregion
}
