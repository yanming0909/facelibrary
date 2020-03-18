package com.idl.stub.util;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zhy.http.okhttp.OkHttpUtils;
import com.zhy.http.okhttp.callback.StringCallback;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;

import okhttp3.Call;

/**
 * 驾驶行为分析
 */
public class DeviceDetechUtil {
    private static final String TAG = DeviceDetechUtil.class.getName();

    private static final String DEVICE_DETECH_URL = "https://aip.baidubce.com/rest/2.0/image-classify/v1/driver_behavior";
    private static final String ACCESS_TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";

    private static final class SingleHolder {
        static final DeviceDetechUtil mInstance = new DeviceDetechUtil();
    }

    public static DeviceDetechUtil getInstance() {
        return DeviceDetechUtil.SingleHolder.mInstance;
    }

    /**
     * 获取access token
     * @param client_id
     * @param client_secret
     * @param callBack
     */
    public static void getAccessToken(String client_id, String client_secret, final OnGetAccessTokenCallBack callBack){
        HashMap<String,String> params = new HashMap<>();
        params.put("grant_type","client_credentials");
        params.put("client_id",client_id);
        params.put("client_secret",client_secret);
        OkHttpUtils.get()
                .url(ACCESS_TOKEN_URL)
                .params(params)
                .build()
                .execute(new StringCallback() {
                    @Override
                    public void onError(Call call, Exception e, int id) {
                        if(callBack!=null){
                            callBack.onFailed();
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onResponse(String response, int id) {
                        try{
                            AccessTokenResponse tokenResponse = new Gson().fromJson(response,AccessTokenResponse.class);
                            if(callBack!=null){
                                callBack.onSuccess(tokenResponse);
                            }
                        }catch (Exception e){
                            if(callBack!=null){
                                callBack.onFailed();
                            }
                            e.printStackTrace();
                        }
                    }
                });
    }

    /**
     * 驾驶行为分析
     * @param accessToken
     * @param img
     * @param type 检测类型
     *             常用:
     *             smoke-吸烟 cellphone-打手机 not_buckling_up-未系安全带
     *             bot_hands_leaving_wheel-双手离开方向盘 not_facing_font-未正视前方
     *             no_face_mask 未正确佩戴口罩
     *             传null 为全部检测
     */
    public static void deviceDetech(String accessToken, Bitmap img, String type, final OnDeviceDetachCallBack callBack){
        if(TextUtils.isEmpty(accessToken)||img==null){
            return ;
        }
        String imgBase64 = null;
        ByteArrayOutputStream baos;
        try {
            baos = new ByteArrayOutputStream();
            img.compress(Bitmap.CompressFormat.JPEG,100,baos);
            baos.flush();
            baos.close();

            byte[] bitmapBytes = baos.toByteArray();
            imgBase64 = Base64.encodeToString(bitmapBytes,Base64.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(imgBase64==null){ //图片数据转换异常
            return ;
        }

        HashMap<String,String> params = new HashMap<>();
        params.put("access_token",accessToken);
        params.put("image",imgBase64);
        //1 标示左舵车 0标示右舵车 默认为1
//        params.put("wheel_location","0");
        if(!TextUtils.isEmpty(type)){
            params.put("type",type);
        }
        HashMap<String,String> header = new HashMap();
        params.put("Content-Type","application/x-www-form-urlencoded");

        OkHttpUtils.post()
                .url(DEVICE_DETECH_URL)
                .params(params)
                .headers(header)
                .build()
                .execute(new StringCallback() {
                    @Override
                    public void onError(Call call, Exception e, int id) {
                        if(call!=null){
                            callBack.onFailed();
                        }
                    }

                    @Override
                    public void onResponse(String response, int id) {
                        try{
                            Log.d(TAG,response);
                            DeviceDetechResponse deviceDetechResponse = new Gson().fromJson(response,DeviceDetechResponse.class);
                            if(callBack!=null){
                                callBack.onSuccess(deviceDetechResponse);
                            }
                        }catch (Exception e){
                            if(callBack!=null){
                                callBack.onFailed();
                            }
                            e.printStackTrace();
                        }
                    }
                });
    }


    public interface OnGetAccessTokenCallBack {
        void onSuccess(AccessTokenResponse response);
        void onFailed();
    }

    public interface  OnDeviceDetachCallBack{
        void onSuccess(DeviceDetechResponse response);
        void onFailed();
    }

}
