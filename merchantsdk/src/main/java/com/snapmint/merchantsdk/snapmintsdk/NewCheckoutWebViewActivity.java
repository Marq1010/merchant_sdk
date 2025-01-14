package com.snapmint.merchantsdk.snapmintsdk;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.normal.TedPermission;
import com.moczul.ok2curl.CurlInterceptor;
import com.snapmint.merchantsdk.BuildConfig;
import com.snapmint.merchantsdk.JSBridge.CheckoutWebViewInterface;
import com.snapmint.merchantsdk.R;
import com.snapmint.merchantsdk.constants.ApiConstant;
import com.snapmint.merchantsdk.constants.SnapmintConfiguration;
import com.snapmint.merchantsdk.databinding.ActivityNewCheckoutWebviewBinding;
import com.snapmint.merchantsdk.utils.CheckoutResponse;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class NewCheckoutWebViewActivity extends AppCompatActivity implements CheckoutResponse {

    private ActivityNewCheckoutWebviewBinding binding;
    private NewCheckoutWebViewActivity mContext;
    private String sucUrl;
    private String failUrl;
    private String baseUrl;
    private WebView newWebView;
    private ValueCallback<Uri> mUploadMessage;
    public ValueCallback<Uri[]> uploadMessage;
    public static final int REQUEST_SELECT_FILE = 100;
    private final static int FILE_CHOOSER_RESULTCODE = 1;
    private String finalData;
    private String apiJson;

    @SuppressLint("ObsoleteSdkInt")
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (requestCode == REQUEST_SELECT_FILE) {
                if (uploadMessage == null)
                    return;
                uploadMessage.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent));
                uploadMessage = null;
            }
        } else if (requestCode == FILE_CHOOSER_RESULTCODE) {
            if (null == mUploadMessage)
                return;
            // Use MainActivity.RESULT_OK if you're implementing WebView inside Fragment
            // Use RESULT_OK only if you're implementing WebView inside an Activity
            Uri result = intent == null || resultCode != NewCheckoutWebViewActivity.RESULT_OK ? null : intent.getData();
            mUploadMessage.onReceiveValue(result);
            mUploadMessage = null;
        } else
            Toast.makeText(mContext, "Failed to Upload Image", Toast.LENGTH_LONG).show();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        binding = ActivityNewCheckoutWebviewBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        mContext = this;
        setContentView(view);
        getBundleData();
        initialise();
    }

    private void getBundleData() {
        Intent intent = getIntent();
        apiJson = intent.getStringExtra(ApiConstant.DATA);
        baseUrl = this.getIntent().getStringExtra("base_url");
        sucUrl = intent.getStringExtra("suc_url");
        failUrl = intent.getStringExtra("fail_url");

    }

    private void initialise() {
        callOkHttpAPi(baseUrl);
    }

    @SuppressLint({"SetJavaScriptEnabled", "NewApi"})
    private void setWebView(String url) {
        WebView.setWebContentsDebuggingEnabled(true);
        WebSettings webSettings = binding.webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setSupportZoom(false);
        webSettings.setSupportMultipleWindows(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setMixedContentMode(0);
        binding.webView.addJavascriptInterface(new CheckoutWebViewInterface(mContext), "Android");
        binding.webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        binding.webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        binding.webView.setWebViewClient(new webClient());
        binding.webView.setWebChromeClient(new webChromeClient());
        binding.webView.loadUrl(url);
        binding.webView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                WebView webView = (WebView) v;
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (webView.canGoBack()) {
                        webView.goBack();
                        return true;
                    }
                }
            }
            return false;
        });

    }

    private void callOkHttpAPi(String baseUrl) {
        try {
            binding.progressBar.setVisibility(View.VISIBLE);
            JSONObject finalData = new JSONObject(apiJson);
            if (!finalData.has("checksum_hash")) {
                finalData.put("checksum_hash", generateCheckSum(finalData.getString("merchant_key") + "|" + finalData.getString("order_id") + "|" + finalData.getString("order_value") + "|" + finalData.getString("full_name") + "|" + finalData.getString("email") + "|" + finalData.getString("merchant_token")));
            }
            final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            OkHttpClient client;
            if (BuildConfig.DEBUG) {
                client = new OkHttpClient.Builder().addInterceptor(new CurlInterceptor(s -> Log.v("Ok2Curl", s))).build();
            } else {
                client = new OkHttpClient();
            }
            RequestBody body = RequestBody.create(String.valueOf(finalData), JSON); // new
            Request request = new Request.Builder().url(baseUrl).post(body).build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        showErrorDialog(e.getMessage());
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    Log.e("TAG", "onResponse: " + response.body());
                    runOnUiThread(() -> {
                        try {
                            JSONObject jsonObject1 = new JSONObject(response.body().string());
                            if (jsonObject1.has("redirect_url")) {
                                try {
                                    setWebView(jsonObject1.getString("redirect_url"));
                                    Log.d("NewCheckout", "onResponse: " + jsonObject1.getString("redirect_url"));
                                } catch (JSONException e) {
                                    binding.progressBar.setVisibility(View.GONE);
                                }
                            } else {
                                binding.progressBar.setVisibility(View.GONE);
                                showErrorDialog(jsonObject1.getString("message"));
                            }
                        } catch (Exception e) {
                            binding.progressBar.setVisibility(View.GONE);
                            showErrorDialog("Incomplete response received from application");
                        }

                    });
                }
            });
        } catch (Exception e) {
            binding.progressBar.setVisibility(View.GONE);
            showErrorDialog("Incomplete response received from application");
        }
    }


    public class webClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try {
                if ((url == null || url.startsWith("http://") || url.startsWith("https://") && !url.contains("/get_mitc_document?"))) {
                    view.loadUrl(url);
                    return false;
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    view.getContext().startActivity(intent);
                    return true;
                } catch (Exception e) {
                    return true;
                }
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
        }


        @Override
        public void onPageFinished(WebView view, final String url) {
            binding.progressBar.setVisibility(View.GONE);
            super.onPageFinished(view, url);
            try {
                if (url != null && (url.contains(sucUrl) || url.contains(failUrl))) {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    (new Handler()).postDelayed(() -> {
                        if (url.contains(sucUrl)) {
                            Intent intent = new Intent();
                            intent.putExtra(SnapmintConfiguration.STATUS, SnapmintConfiguration.SUCCESS);
                            setResult(RESULT_OK, intent);
                            finish();
                        } else if (url.contains(failUrl)) {
                            Intent intent = new Intent();
                            intent.putExtra(SnapmintConfiguration.STATUS, SnapmintConfiguration.FAILED);
                            setResult(RESULT_OK, intent);
                            finish();
                        } else {
                            binding.progressBar.setVisibility(View.GONE);
                            Log.d("view", "not removed");
                        }
                    }, 2000L);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setMessage(R.string.notification_error_ssl_cert_invalid);
            builder.setPositiveButton("continue", (dialog, which) -> handler.proceed());
            builder.setNegativeButton("cancel", (dialog, which) -> handler.cancel());
            final AlertDialog dialog = builder.create();
            dialog.show();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
        }

        @Override
        public void onLoadResource(WebView view, String url) {
            super.onLoadResource(view, url);
            view.loadUrl("javascript:document.querySelectorAll('.snap-nav').forEach(function(a){\n" + "a.remove()\n" + "});" + "document.querySelectorAll('.mob-footer-new').forEach(function(a){\n" + "a.remove()\n" + "});" + "document.querySelectorAll('.footer').forEach(function(a){\n" + "a.remove()\n" + "});" + "document.body.style.paddingTop='0'; void 0");
        }
    }

    public class webChromeClient extends WebChromeClient {
        @SuppressLint("SetJavaScriptEnabled")
        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
//            binding.webView.setVisibility(View.GONE);
            newWebView = new WebView(mContext);
            newWebView.getSettings().setJavaScriptEnabled(true);
            newWebView.getSettings().setDomStorageEnabled(true);
            newWebView.getSettings().setSupportZoom(true);
            newWebView.getSettings().setBuiltInZoomControls(true);
            newWebView.getSettings().setSupportMultipleWindows(true);
            newWebView.getSettings().setUseWideViewPort(true);
            newWebView.getSettings().setLoadWithOverviewMode(true);
            newWebView.getSettings().setAllowFileAccess(true);
            newWebView.getSettings().setAllowContentAccess(true);
            newWebView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);

            newWebView.setWebViewClient(new WebViewClient() {
               /* @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    view.loadUrl(url);
                    return true;
                }*/
            });
            newWebView.setWebChromeClient(new webChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    super.onProgressChanged(view, newProgress);
                }

                // For Lollipop 5.0+ Devices
                public boolean onShowFileChooser(WebView mWebView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                    return openFileChooser(filePathCallback, fileChooserParams);
                }

                @Override
                public void onPermissionRequest(PermissionRequest request) {
                    super.onPermissionRequest(request);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        request.grant(request.getResources());
                    }
                }

                @Override
                public void onCloseWindow(WebView window) {
                    super.onCloseWindow(window);
                    window.destroy();
                    binding.webView.removeView(newWebView);
                }
            });
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            newWebView.setLayoutParams(params);
            view.addView(newWebView);
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(newWebView);
            resultMsg.sendToTarget();
            return true;
        }

        @Override
        public void onCloseWindow(WebView window) {
            super.onCloseWindow(window);
            window.destroy();
            newWebView.setVisibility(View.GONE);
            binding.webView.setVisibility(View.VISIBLE);

        }

        public boolean onShowFileChooser(WebView mWebView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            return openFileChooser(filePathCallback, fileChooserParams);
        }


        @SuppressLint("NewApi")
        @Override
        public void onPermissionRequest(PermissionRequest request) {
            try {
                if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
                    TedPermission.create().setPermissionListener(new PermissionListener() {
                        @SuppressLint("NewApi")
                        @Override
                        public void onPermissionGranted() {
                            request.grant(request.getResources());
                        }

                        @Override
                        public void onPermissionDenied(List<String> deniedPermissions) {
                            Toast.makeText(mContext, R.string.please_give_camera_permission_without_that_we_cannot, Toast.LENGTH_SHORT).show();
                        }
                    }).setDeniedMessage(R.string.please_give_camera_permission_without_that_we_cannot).setPermissions(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE).check();
                } else {
                    request.grant(request.getResources());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            String url = view.getUrl();
            try {
                if (url != null && (url.contains(sucUrl) || url.contains(failUrl))) {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    (new Handler()).postDelayed(() -> {
                        if (url.contains(sucUrl)) {
                            Intent intent = new Intent();
                            intent.putExtra(SnapmintConfiguration.STATUS, SnapmintConfiguration.SUCCESS);
                            setResult(RESULT_OK, intent);
                            finish();
                        } else if (url.contains(failUrl)) {
                            Intent intent = new Intent();
                            intent.putExtra(SnapmintConfiguration.STATUS, SnapmintConfiguration.FAILED);
                            setResult(RESULT_OK, intent);
                            finish();
                        } else {
                            binding.progressBar.setVisibility(View.GONE);
                            Log.d("view", "not removed");
                        }
                    }, 2000L);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean openFileChooser(ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
        if (uploadMessage != null) {
            uploadMessage.onReceiveValue(null);
            uploadMessage = null;
        }

        uploadMessage = filePathCallback;

        Intent intent = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent = fileChooserParams.createIntent();
        }
        try {
            startActivityForResult(intent, REQUEST_SELECT_FILE);
        } catch (ActivityNotFoundException e) {
            uploadMessage = null;
            Toast.makeText(mContext, "Cannot Open File Chooser", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private String generateCheckSum(String checkSumString) {
        Log.d("STR", checkSumString);
        String generatedCheckSum = "";
        MessageDigest messageDigest = null;
        try {
            messageDigest = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] digest = messageDigest.digest(checkSumString.getBytes());
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            stringBuilder.append(Integer.toString((digest[i] & 0xff) + 0x100, 16).substring(1));
        }
        return stringBuilder.toString();
    }

    @Override
    public void onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack();
        } else if (newWebView != null && newWebView.canGoBack()) {
            newWebView.goBack();
        } else {
            if (newWebView != null) {
                binding.webView.removeView(newWebView);
                newWebView.destroy();
                newWebView = null;
            } else {
                Intent intent = new Intent();
                intent.putExtra(SnapmintConfiguration.STATUS, SnapmintConfiguration.FAILED);
                setResult(RESULT_OK, intent);
                finish();
            }
        }
    }

    @Override
    public void handlePaymentResponse(@Nullable String code, @Nullable String message) {
        Intent returnIntent = new Intent();
        returnIntent.putExtra("status_code", code);
        returnIntent.putExtra("status_msg", message);
        setResult(Activity.RESULT_OK, returnIntent);
        finish();
    }

    private void showErrorDialog(String message) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setMessage(message);
            builder.setTitle("Error !");
            builder.setNegativeButton("Ok", (dialog, which) -> {
                        dialog.cancel();
                        onBackPressed();
                    }

            );
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        });
    }

    @Override
    public void orderSuccess(String data) {
        Intent intent = new Intent();
        intent.putExtra(SnapmintConfiguration.STATUS, SnapmintConfiguration.SUCCESS);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void orderFailed() {
        Intent intent = new Intent();
        intent.putExtra(SnapmintConfiguration.STATUS, SnapmintConfiguration.FAILED);
        setResult(RESULT_OK, intent);
        finish();
    }
}