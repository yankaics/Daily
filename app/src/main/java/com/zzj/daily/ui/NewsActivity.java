package com.zzj.daily.ui;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.nfc.Tag;
import android.os.Bundle;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.zzj.daily.R;
import com.zzj.daily.bean.NewsEntity;
import com.zzj.daily.cache.ACache;
import com.zzj.daily.conn.ZhiHuDailyApi;
import com.zzj.daily.support.MyApplication;
import com.zzj.daily.task.DownloadImageTask;
import com.zzj.daily.task.NewsTask;

/**
 * Created by xiaoJ on 2016/3/14.
 * 新闻详细页activity
 */
public class NewsActivity extends AppCompatActivity {
    private ACache aCache;
    private Toolbar toolbar;
    private ImageView imageView;
    private TextView textView_title;
    private TextView textView_imageSource;
    private WebView webView;
    private SharedPreferences sharedPreferences;
    private static NewsEntity newsEntity;
    private MyScrollView myScrollView;
    private int mTouchSlop;
    private float mFirstY, mCurrentY;
    private boolean mToolbarShow = true;
    private Animator mAnimator;
    private FrameLayout frameLayout;
    //是否为夜间模式 是->true 否->false
    private static boolean isNightMode;

    public static void setNewsEntity(NewsEntity newsEntity) {
        NewsActivity.newsEntity = newsEntity;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //activity被创建时从sharedPreferences获取当前主题模式
        sharedPreferences = this.getSharedPreferences("isNightMode", Context.MODE_PRIVATE);
        isNightMode = sharedPreferences.getBoolean("mode", false);
        if (isNightMode) {
            //如果是夜间模式,设置theme为夜间模式
            setTheme(R.style.MyTheme_Night);
        } else {
            setTheme(R.style.MyTheme_Day);
        }
        setContentView(R.layout.news_layout);
        initView();
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        //设置返回按钮的图片
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_keyboard_backspace_black_18dp);
        mTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        Intent intent = getIntent();
        //从intent中取出key为Id的数据
        String id = String.valueOf(intent.getIntExtra("id", -1));
        webView.addJavascriptInterface(new JavascriptInterface(this), "imagelistner");
        webView.setWebViewClient(new MyWebViewClient());
        //若当前硬盘缓存存在该新闻，则从缓存中取出，否则则从网络请求
        if (aCache.getAsObject(ZhiHuDailyApi.news + id) != null) {
            newsEntity = (NewsEntity) aCache.getAsObject(ZhiHuDailyApi.news + id);
            this.textView_title.setText(newsEntity.getTitle());
            this.textView_imageSource.setText(newsEntity.getImage_source());
            //若当前硬盘缓存存在该图片，则从缓存取出，否则则从网络请求
            if (aCache.getAsBitmap(newsEntity.getImage()) != null) {
                this.imageView.setImageBitmap(aCache.getAsBitmap(newsEntity.getImage()));
            } else {
                new DownloadImageTask(this.imageView).execute(new String[]{newsEntity.getImage()});
            }
            String css = "<link rel=\"stylesheet\" href=\"file:///android_asset/news.css\" type=\"text/css\">";
            String html = "";
            //判断是否为夜间模式
            if (isNightMode) {
                //加载body class = night 的css样式以实现夜间模式
                html = "<html><head>" + css + "</head><body class=\"night\">" + newsEntity.getBody() + "</body></html>";
            } else {
                html = "<html><head>" + css + "</head><body>" + newsEntity.getBody() + "</body></html>";
            }
            webView.loadDataWithBaseURL("x-data://base", html, "text/html", "UTF-8", null);
        } else {
            //启动一个异步任务从网络获取数据
            new NewsTask(imageView, textView_title, textView_imageSource, webView, isNightMode).execute(new String[]{ZhiHuDailyApi.news + id});
        }

        myScrollView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mFirstY = event.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        mCurrentY = event.getY();
                        if (mCurrentY - mFirstY > mTouchSlop) {
                            if (!mToolbarShow) {
                                toolbarAnim(0);
                                mToolbarShow = !mToolbarShow;
                            }
                        } else if (mFirstY - mCurrentY > mTouchSlop) {
                            if (mToolbarShow) {
                                toolbarAnim(1);
                                mToolbarShow = !mToolbarShow;
                            }
                        }

                        break;
                    case MotionEvent.ACTION_UP:
                        break;
                }
                return false;
            }
        });
    }

    /**
     * Toolbar隐藏显示动画
     *
     * @param flag 0为显示，1为隐藏
     */
    private void toolbarAnim(int flag) {
        if (mAnimator != null && mAnimator.isRunning()) {
            mAnimator.cancel();
        }
        if (flag == 0) {
            mAnimator = ObjectAnimator.ofFloat(toolbar, "translationY", toolbar.getTranslationY(), 0);
        }
        if (flag == 1) {
            mAnimator = ObjectAnimator.ofFloat(toolbar, "translationY", toolbar.getTranslationY(), -toolbar.getHeight());
        }
        mAnimator.start();

    }

    ;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.news_menu, menu);
        return true;
    }

    /**
     * 重写事件监听方法
     *
     * @param item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            //点击左上角返回按钮finish当前activity
            case android.R.id.home:
                finish();
                break;
            //点击分享按钮触发事件
            case R.id.share:
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.setType("text/plain");
                //将数据放进intent
                intent.putExtra(Intent.EXTRA_TEXT, newsEntity.getTitle() + "(分享自@日报)" + newsEntity.getShare_url());
                startActivity(intent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 初始化控件
     */
    private void initView() {
        //获取缓存类acache
        this.aCache = ACache.get(MyApplication.getContext());
        toolbar = (Toolbar) findViewById(R.id.news_toolbar);
        imageView = (ImageView) findViewById(R.id.new_image);
        textView_title = (TextView) findViewById(R.id.new_title);
        textView_imageSource = (TextView) findViewById(R.id.image_source);
        webView = (WebView) findViewById(R.id.WebView);
        myScrollView = (MyScrollView) findViewById(R.id.myScrollView);
        frameLayout = (FrameLayout) findViewById(R.id.frameLayoutContent);
        //允许执行Js脚本
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setBuiltInZoomControls(false);
        webView.getSettings().setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
    }

    // js通信接口
    public class JavascriptInterface {

        private Context context;

        public JavascriptInterface(Context context) {
            this.context = context;
        }

        @android.webkit.JavascriptInterface
        public void openImage(String img) {
            Intent intent = new Intent();
            intent.putExtra("url", img);
            intent.setClass(NewsActivity.this, ShowImageActivity.class);
            startActivity(intent);
        }
    }

    public class MyWebViewClient extends WebViewClient {

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            view.loadUrl("javascript:(function(){" +
                    "var objs = document.getElementsByTagName(\"img\"); " +
                    "for(var i=0;i<objs.length;i++)  " +
                    "{"
                    + "    objs[i].onclick=function()  " +
                    "    {  "
                    + "        window.imagelistner.openImage(this.src);  " +
                    "    }  " +
                    "}" +
                    "})()");
        }

    }
}
