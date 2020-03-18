package com.idl.stub.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.os.Handler;
import android.util.Log;
import android.view.TextureView;

import com.baidu.idl.main.facesdk.FaceInfo;
import com.baidu.idl.main.facesdk.model.BDFaceImageInstance;
import com.baidu.idl.main.facesdk.model.BDFaceSDKCommon;
import com.idl.stub.BitmapUtils;
import com.idl.stub.ConfigUtils;
import com.idl.stub.FaceDetectCallBack;
import com.idl.stub.FaceOnDrawTexturViewUtil;
import com.idl.stub.FaceSDKManager;
import com.idl.stub.FaceTrackManager;
import com.idl.stub.LivenessModel;
import com.idl.stub.SdkInitListener;
import com.idl.stub.SingleBaseConfig;

import java.util.ArrayList;
import java.util.List;


public class FaceDetectUtil  {
    private static final String TAG = FaceDetectUtil.class.getName();

    //标识sdk是否加载完成，加载model需要时间
    private boolean mIsSDKModelInitFinish = false;
    private boolean mIsSDKLicenseSuccess = false;
    //目标识别数据
    private Bitmap targetBitmap;
    private byte[] targetFeature = new byte[512];
    private boolean targetFeatureSync = false;

    private InfoListener mInfoListener;
    // 人脸框绘制
    private RectF rectF = new RectF();
    private Paint paint = new Paint();

    private int retryInitTime = 0;
    //初始化失败重试次数(防止在线初始化时网络异常)
    private static final int INIT_MAX_TIME = 5;

    private static final class SingleHolder {
        static final FaceDetectUtil mInstance = new FaceDetectUtil();
    }

    public static FaceDetectUtil getInstance() {
        return SingleHolder.mInstance;
    }


    /**
     * 初始化
     */
    public void init(final Context context, final String key, final boolean initOffline) {
        //初始化config
        if (ConfigUtils.isConfigExit()) {
            ConfigUtils.initConfig();
        }
        retryInitTime = 0;
        requestInit(context,key,initOffline);
    }

    /**
     * 更新识别阀值
     * @param threshold
     */
    public void updateThreshold(int threshold){
        //识别阀值
        SingleBaseConfig.getBaseConfig().setThreshold(threshold);
    }

    public void requestInit(final Context context, final String key, final boolean initOffline){
        String offLineKey = "";
        String onLineKey = "";
        if(initOffline){
            offLineKey = key;
        }else{
            onLineKey = key;
        }

        FaceSDKManager.getInstance().init(context, new SdkInitListener() {
            @Override
            public void initStart() {
                Log.d(TAG, "initStart");
            }

            @Override
            public void initLicenseSuccess() {
                Log.d(TAG, "initLicenseSuccess");
                mIsSDKLicenseSuccess = true;
                if(mInfoListener!=null&&checkInitState()){
                    mInfoListener.onInit();
                }
            }

            @Override
            public void initLicenseFail(int errorCode, String msg) {
                Log.d(TAG, "initLicenseFail");
                if(retryInitTime<INIT_MAX_TIME){
                    //初始化失败重试
                    retryInitTime++;
                    new Handler(context.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            requestInit(context,key,initOffline);
                        }
                    },1000);
                }else{
                    Log.d(TAG, "final initLicenseFail");
                }

            }

            @Override
            public void initModelSuccess() {
                Log.d(TAG, "initModelSuccess");
                //加载模型成功说明SDK已完全加载
                mIsSDKModelInitFinish = true;
                if(mInfoListener!=null&&checkInitState()){
                    mInfoListener.onInit();
                }
            }

            @Override
            public void initModelFail(int errorCode, String msg) {
                Log.d(TAG, "initModelFail");
                if(retryInitTime<INIT_MAX_TIME){
                    //初始化失败重试
                    retryInitTime++;
                    new Handler(context.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            requestInit(context,key,initOffline);
                        }
                    },1000);
                }else{
                    Log.d(TAG, "final initModelFail");
                }
            }
        },offLineKey,onLineKey);
    }

    public void compareVideo(Feature feature, final byte[] rgbData, int width, int height ) {
        if(feature==null){
            return;
        }
        ArrayList<Feature> features = new ArrayList<>();
        features.add(feature);
        compareVideo(features,rgbData,width,height);
    }

    public void compareVideo(final List<Feature> features, final byte[] rgbData, int width, int height){
        if(features==null||features.isEmpty()){
            return;
        }
        //打开活体检测
        FaceTrackManager.getInstance().setAliving(true);
        FaceTrackManager.getInstance().faceTrack(rgbData, width, height, new FaceDetectCallBack() {
            @Override
            public void onFaceDetectCallback(LivenessModel livenessModel) {
                if(checkLiveModel(livenessModel,true,false,false)){
                    //活体检验通过,提取图片特征码
                    BDFaceImageInstance image = livenessModel.getBdFaceImageInstance();
                    targetBitmap = BitmapUtils.getInstaceBmp(image);
                    targetFeatureSync = syncFeature(targetBitmap, targetFeature);
                    if(targetFeatureSync) {
                        for (Feature featureData : features) {
                            float score = match(featureData.feature,targetFeature);
                            int idFeatureValue = SingleBaseConfig.getBaseConfig().getThreshold();
                            boolean success = score>=idFeatureValue;
                            setResult(success,score,featureData.id);
                            if(success){//成功直接跳出循环
                                break;
                            }
                        }
                    }

                    // 流程结束销毁图片，开始下一帧图片检测，否则内存泄露
                    image.destory();
                }

            }

            @Override
            public void onTip(int code, final String msg) {
                Log.e("FaceDetect","Error Tip:"+msg);
            }

            @Override
            public void onFaceDetectDarwCallback(LivenessModel livenessModel) {
                // 绘制人脸框
                //FaceTrackManager 原生代码不调用这个callback,需要手动添加相关代码
            }
        });
    }

    public void compareVideo(Feature feature, final byte[] rgbData, final byte[] nirData,
                             final byte[] depthData, int width, int height, int liveCheckMode, int featureCheckMode,
                             final TextureView textureView) {
        if(feature==null){
            return;
        }
        ArrayList<Feature> features = new ArrayList<>();
        features.add(feature);
        compareVideo(features,rgbData,nirData,depthData,width,height,liveCheckMode,featureCheckMode,textureView);
    }


    /**
     * 视频提取图片并比较
     *
     * @param features         基础特征值
     * @param rgbData          rgb数据
     * @param nirData          红外数据
     * @param depthData        深度数据
     * @param width            数据宽度
     * @param height           数据高度
     * @param liveCheckMode    活体检测mode
     * @param featureCheckMode 特征提取mode
     * @param textureView      显示texture
     */
    public void compareVideo(final List<Feature> features, final byte[] rgbData, final byte[] nirData,
                             final byte[] depthData, int width, int height, int liveCheckMode, int featureCheckMode,
                             final TextureView textureView) {
        if(!checkInitState()) {
            return;
        }
        if(features==null||features.isEmpty()){
            return;
        }
        if (rgbData == null && nirData == null && depthData == null) {//视频数据为空
            return;
        }
        final boolean checkRgb = rgbData!=null;
        final boolean checkNir = nirData!=null;
        final boolean checkDepth = depthData!=null;
        FaceSDKManager.getInstance().onDetectCheck(rgbData, nirData, depthData, height,
                width, liveCheckMode, featureCheckMode, new FaceDetectCallBack() {
                    @Override
                    public void onFaceDetectCallback(LivenessModel livenessModel) {
                        if(checkLiveModel(livenessModel,checkRgb,checkNir,checkDepth)){
                            //活体检验通过,提取图片特征码
                            BDFaceImageInstance image = livenessModel.getBdFaceImageInstance();
                            targetBitmap = BitmapUtils.getInstaceBmp(image);
                            targetFeatureSync = syncFeature(targetBitmap, targetFeature);
                            for (Feature featureData : features) {
                                float score = match(featureData.feature,targetFeature);
                                int idFeatureValue = SingleBaseConfig.getBaseConfig().getThreshold();
                                boolean success = score>=idFeatureValue;
                                setResult(success,score,featureData.id);
                                if(success){//成功直接跳出循环
                                    break;
                                }
                            }
                            // 流程结束销毁图片，开始下一帧图片检测，否则内存泄露
                            image.destory();
                        }
                    }

                    @Override
                    public void onTip(int code, String msg) {
                        Log.e("FaceDetect","Error Tip:"+msg);
                    }

                    @Override
                    public void onFaceDetectDarwCallback(LivenessModel livenessModel) {
                        if (textureView != null) {
                            showFrame(livenessModel, textureView);
                        }
                    }
                });
    }

    //校验活体信息
    private boolean checkLiveModel(LivenessModel livenessModel, boolean checkRgb, boolean checkNir, boolean checkDepth){
        if (livenessModel != null) {
            float rgbLiveScore = livenessModel.getRgbLivenessScore();
            float nirLiveScore = livenessModel.getIrLivenessScore();
            float depthLiveScore = livenessModel.getDepthLivenessScore();
            boolean rgbLiveCheck = (!checkRgb|| rgbLiveScore >= SingleBaseConfig.getBaseConfig().getRgbLiveScore());
            boolean nirLiveCheck = (!checkNir|| nirLiveScore >= SingleBaseConfig.getBaseConfig().getNirLiveScore());
            boolean depthLiveCheck = (!checkDepth || depthLiveScore >= SingleBaseConfig.getBaseConfig().getDepthLiveScore());
            return (rgbLiveCheck || nirLiveCheck) && depthLiveCheck;
        }
        return false;
    }

    public void clear() {
        if(targetBitmap!=null&&!targetBitmap.isRecycled()){
            targetBitmap.recycle();
        }
        targetFeatureSync = false;
        targetFeature = new byte[512];
    }

    /**
     * 绘制人脸框
     */
    private void showFrame(final LivenessModel model, TextureView textureView) {
        Canvas canvas = textureView.lockCanvas();

        if (canvas == null) {
            textureView.unlockCanvasAndPost(canvas);
            return;
        }
        if (model == null) {
            // 清空canvas
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            textureView.unlockCanvasAndPost(canvas);
            return;
        }
        FaceInfo[] faceInfos = model.getTrackFaceInfo();
        if (faceInfos == null || faceInfos.length == 0) {
            // 清空canvas
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            textureView.unlockCanvasAndPost(canvas);
            return;
        }
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        FaceInfo faceInfo = faceInfos[0];

        rectF.set(FaceOnDrawTexturViewUtil.getFaceRectTwo(faceInfo));
//                 检测图片的坐标和显示的坐标不一样，需要转换。
        FaceOnDrawTexturViewUtil.mapFromOriginalRect(rectF,
                textureView, model.getBdFaceImageInstance());
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        // 绘制框
        canvas.drawRect(rectF, paint);
        textureView.unlockCanvasAndPost(canvas);
    }


    /**
     * 比对特征码
     *
     * @param feature1 基础特征码
     * @param feature2 第二特征码
     * @return
     */
    public float match(byte[] feature1, byte[] feature2) {
        //  比较两个人脸
        return FaceSDKManager.getInstance().getFaceFeature().featureCompare(
                BDFaceSDKCommon.FeatureType.BDFACE_FEATURE_TYPE_ID_PHOTO,
                feature1, feature2, true);
    }

    private void setResult(boolean success, float code,String id) {
        if (mInfoListener != null) {
            if (success) {
                mInfoListener.success(code, targetBitmap,id);
                clear();
            } else {
                mInfoListener.error(code);
            }
        }
    }

    /**
     * bitmap -提取特征值
     *
     * @param bitmap  图片数据
     * @param feature 特征数据
     */

    public boolean syncFeature(final Bitmap bitmap, final byte[] feature) {
        float ret = -1;
        BDFaceImageInstance rgbInstance = new BDFaceImageInstance(bitmap);
        FaceInfo[] faceInfos = FaceSDKManager.getInstance().getFaceDetect()
                .detect(BDFaceSDKCommon.DetectType.DETECT_VIS, rgbInstance);
        // 检测结果判断
        if (faceInfos != null && faceInfos.length > 0) {
            ret = FaceSDKManager.getInstance().getFaceFeature().feature(BDFaceSDKCommon.FeatureType.
                    BDFACE_FEATURE_TYPE_ID_PHOTO, rgbInstance, faceInfos[0].landmarks, feature);
            return ret == 128;
        }
        return false;
    }


    public boolean checkInitState() {
        return mIsSDKLicenseSuccess && mIsSDKModelInitFinish;
    }

    public void setInfoListener(InfoListener listener){
        this.mInfoListener = listener;
    }

    public void removeInfoListener(){
        this.mInfoListener = null;
    }

    public interface InfoListener {
        //SDK加载完成
        void onInit();

        void success(float code, Bitmap pic, String id);

        //仅对比失败时调用,图片质量问题导致不通过检测不调用
        void error(float code);
    }

    public static class Feature{
        public String id;
        public byte[] feature;
    }


}
