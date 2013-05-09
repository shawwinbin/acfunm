
package tv.avfun;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tv.avfun.adapter.DetailAdaper;
import tv.avfun.api.ApiParser;
import tv.avfun.app.DownloadService;
import tv.avfun.app.DownloadService.DownloadBinder;
import tv.avfun.db.DBService;
import tv.avfun.entity.Contents;
import tv.avfun.entity.VideoInfo;
import tv.avfun.entity.VideoInfo.VideoItem;
import tv.avfun.util.ArrayUtil;
import tv.avfun.util.NetWorkUtil;
import tv.avfun.util.StringUtil;
import tv.avfun.util.lzlist.ImageLoader;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Html;
import android.text.util.Linkify;
import android.text.util.Linkify.TransformFilter;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.actionbarsherlock.widget.SearchView;
import com.actionbarsherlock.widget.ShareActionProvider;
import com.umeng.analytics.MobclickAgent;

public class DetailActivity extends SherlockActivity implements OnItemClickListener, OnClickListener {

    private static final String TAG        = DetailActivity.class.getSimpleName();
    private static final String LOADING    = "正在加载...";
    private static final int    TAG_RELOAD = 100;
    private static final int    TAG_PLAY   = 200;
    private DownloadBinder      downloadService;
    private Intent              mIntent;
    /**
     * 来自：0 首页 1历史、搜索 2 Action_View av://12345
     */
    private int                 from;
    private ImageLoader         mImgLoader;
    private TextView            tvTitle, tvViews, tvComments;
    private TextView            tvUserName, tvBtnPlay, tvDesc;
    private ImageView           ivTitleImg;
    private String              aid;
    private String              title;
    private int                 channelid;
    private String              description;
    private ListView            mListView;
    private List<VideoItem>     mData;
    private DetailAdaper        adapter;
    private LayoutInflater      mInflater;
    private View                mLoadView;
    private boolean             isFavorite;
    private VideoInfo           mVideoInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        Intent service = new Intent(this, DownloadService.class);
        bindService(service, conn, BIND_AUTO_CREATE);

        mIntent = getIntent();
        if (Intent.ACTION_VIEW.equals(mIntent.getAction())) {
            from = "av".equalsIgnoreCase(mIntent.getScheme()) ? 2 : 0;
            if (BuildConfig.DEBUG)
                Log.i(TAG, "看av: " + mIntent.getDataString());
        } else {
            from = mIntent.getIntExtra("from", 0);
        }
        mImgLoader = ImageLoader.getInstance();
        initBar();
        initview();
        loadData();
    }
    private ServiceConnection conn = new DownloadServiceConnection();
    private void initBar() {
        getSupportActionBar().setBackgroundDrawable(this.getResources().getDrawable(R.drawable.ab_transparent));
        Drawable bg = getResources().getDrawable(R.drawable.border_bg);
        getSupportActionBar().setSplitBackgroundDrawable(bg);
        //forceShowActionBarOverflowMenu();
    }
    /*private void forceShowActionBarOverflowMenu() {
        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if (menuKeyField != null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        } catch (Exception ignored) {

        }
    }*/

    private void loadData() {
        new RequestDetailTask().execute();
    }

    private class RequestDetailTask extends AsyncTask<Void, Void, Boolean> {

        private View     progress;
        private TextView text;

        @Override
        protected void onPreExecute() {
            progress = mLoadView.findViewById(R.id.list_loadview_progress);
            progress.setVisibility(View.VISIBLE);
            text = (TextView) mLoadView.findViewById(R.id.list_loadview_text);
            text.setText(R.string.loading);
            mLoadView.setVisibility(View.VISIBLE);

            mListView.setVisibility(View.INVISIBLE);

        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                if (NetWorkUtil.isNetworkAvailable(getApplicationContext())) {
                    mVideoInfo = ApiParser.getVideoInfoByAid(aid);
                    mData.addAll(mVideoInfo.parts);
                }
                // 下载功能只支持9+
                if (Build.VERSION.SDK_INT >= 9) {
                    // 先从downloadlist数据库中查aid 对应的video item
                    List<VideoItem> items = new DBService(getApplicationContext()).getVideoItems(aid);
                    for (VideoItem item : items) {
                        mData.remove(item); // call equals
                    }
                    mData.addAll(items);
                }

            } catch (Exception e) {
                // TODO 向umeng 发送自定义事件: aid获取失败
                if (BuildConfig.DEBUG)
                    Log.w(TAG, "获取数据出错！" + aid, e);
            }

            return ArrayUtil.validate(mData);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                if (from > 0) {
                    tvUserName.setText(mVideoInfo.upman==null?"无名氏":mVideoInfo.upman);
                    tvViews.setText(mVideoInfo.views + "");
                    tvComments.setText(mVideoInfo.comments + "");
                    description = mVideoInfo.description;
                    setDescription(tvDesc);
                    channelid = mVideoInfo.channelId;
                    title = mVideoInfo.title;
                    tvTitle.setText(title);
                    String imgurl = mVideoInfo.titleImage;
                    if (StringUtil.validate(imgurl)) {
                        mImgLoader.displayImage(imgurl, ivTitleImg);
                    } else {
                        ivTitleImg.setBackgroundResource(R.drawable.no_picture);
                    }
                }
                tvBtnPlay.setText("播放");
                tvBtnPlay.setOnClickListener(DetailActivity.this);
                mListView.setVisibility(View.VISIBLE);
                mLoadView.setVisibility(View.GONE);
                adapter.setData(mData);
            } else {
                progress.setVisibility(View.GONE);
                text.setText(R.string.reloading);
                mLoadView.setTag(TAG_RELOAD);
                mLoadView.setEnabled(true);
                mLoadView.setOnClickListener(DetailActivity.this);
            }

        }
    }

    private void initview() {
        setContentView(R.layout.detail_layout);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        tvTitle = (TextView) findViewById(R.id.detail_title);
        tvUserName = (TextView) findViewById(R.id.detail_usename);
        tvViews = (TextView) findViewById(R.id.detail_views);
        tvComments = (TextView) findViewById(R.id.detail_comment);
        ivTitleImg = (ImageView) findViewById(R.id.detail_img);
        tvBtnPlay = (TextView) findViewById(R.id.detail_play_btn);
        tvBtnPlay.setTag(TAG_PLAY);
        tvBtnPlay.setOnClickListener(this);
        tvDesc = (TextView) findViewById(R.id.detail_desc);
        if (from == 2) {
            // av://ac000000
            aid = mIntent.getDataString().substring(7);
        } else {
            Contents c = (Contents) mIntent.getExtras().get("contents");
            if (c == null)
                throw new IllegalArgumentException("你从异次元来的吗？");
            title = StringUtil.getSource(c.getTitle());
            aid = c.getAid();
            channelid = c.getChannelId();
            mImgLoader.displayImage(c.getTitleImg(), ivTitleImg);
            tvUserName.setText(c.getUsername());
            tvViews.setText(String.valueOf(c.getViews()));
            tvComments.setText(String.valueOf(c.getComments()));
            description = c.getDescription();
            setDescription(tvDesc);
            tvBtnPlay.setText(LOADING);
            tvTitle.setText(c.getTitle());

        }
        getSupportActionBar().setTitle("ac" + aid);
        isFavorite = new DBService(this).isFaved(aid);
        // TODO 向umeng发送 事件：查看aid
        if (from > 0) {
            ivTitleImg.setBackgroundResource(R.drawable.no_picture);
            tvUserName.setText(LOADING);
            tvViews.setText(LOADING);
            tvComments.setText(LOADING);
            tvBtnPlay.setText(LOADING);
            tvDesc.setText(LOADING);
        }
        mListView = (ListView) findViewById(R.id.detail_listview);
        mInflater = LayoutInflater.from(DetailActivity.this);
        mData = new ArrayList<VideoInfo.VideoItem>();
        adapter = new DetailAdaper(mInflater, mData);
        mListView.setAdapter(adapter);
        mListView.setDuplicateParentStateEnabled(true);
        mListView.setOnItemClickListener(this);
        mLoadView = findViewById(R.id.load_view);
    }



    private class DownloadServiceConnection implements ServiceConnection {

        @Override
        public void onServiceDisconnected(ComponentName name) {}

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            downloadService = (DownloadBinder) service;
        }
    };

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // 播放
        VideoItem item = (VideoItem) parent.getItemAtPosition(position);
        startPlay(item);
    }

    private void startPlay(VideoItem item) {
        addToHistory();
        Intent intent = new Intent(DetailActivity.this, SectionActivity.class);
        intent.putExtra("item", item);
        startActivity(intent);
    }

    private void addToHistory() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日 hh:mm");
        new DBService(this).addtoHis(aid, title, sdf.format(new Date()), 0, channelid);
    }

    @Override
    public void onClick(View v) {
        Object o = v.getTag();
        if (o == null)
            return;
        switch (((Integer) o).intValue()) {
        // 播放按钮
        case TAG_PLAY:
            if (mData.isEmpty())
                return;
            startPlay(mData.get(0));
            break;

        // 重新加载
        case TAG_RELOAD:
            v.setEnabled(false);
            loadData();
            break;
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getSupportMenuInflater().inflate(R.menu.share_action_provider, menu);
        MenuItem shareItem = menu.findItem(R.id.menu_item_share_action_provider_action_bar);
        ShareActionProvider shareProvider = (ShareActionProvider) shareItem.getActionProvider();
        // shareProvider.setShareHistoryFileName(ShareActionProvider.DEFAULT_SHARE_HISTORY_FILE_NAME);
        shareProvider.setShareIntent(createShareIntent());
        if (isFavorite) {
            menu.findItem(R.id.menu_item_fov_action_provider_action_bar).setIcon(R.drawable.rating_favorite_p);
        }
        SearchView searchView = new SearchView(getActionBar().getThemedContext());
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchableInfo info = searchManager.getSearchableInfo(getComponentName());
        searchView.setSearchableInfo(info);
        searchView.setQueryHint("搜索...");
        menu.add("Search").setIcon(R.drawable.action_search).setActionView(searchView)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS| MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW/*MenuItem.SHOW_AS_ACTION_IF_ROOM*/);
        View v = searchView.findViewById(R.id.abs__search_plate);
        v.setBackgroundResource(R.drawable.edit_text_holo_light);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            this.finish();
            break;

        case R.id.menu_item_fov_action_provider_action_bar:
            if (isFavorite) {
                new DBService(this).delFav(aid);
                isFavorite = false;
                item.setIcon(R.drawable.rating_favorite);
                Toast.makeText(this, "取消成功", Toast.LENGTH_SHORT).show();
            } else {
                new DBService(this).addtoFav(aid, title, 0, channelid);
                isFavorite = true;
                item.setIcon(R.drawable.rating_favorite_p);
                Toast.makeText(this, "收藏成功", Toast.LENGTH_SHORT).show();
            }
            break;
        case R.id.menu_item_comment:
            Intent intent = new Intent(DetailActivity.this, CommentsActivity.class);
            intent.putExtra("aid", aid);
            startActivity(intent);
            break;
        }
        return true;
    }

    public void setDescription(TextView text) {
        Pattern wiki = Pattern.compile("\\[wiki([^\\[]+)\\]", Pattern.CASE_INSENSITIVE);
        String localDesc = StringUtil.getSource(description);
        text.setText(Html.fromHtml(localDesc));
        Linkify.addLinks(text, wiki, null, null, new TransformFilter() {

            @Override
            public String transformUrl(Matcher match, String url) {
                String t = match.group(1);
                return "http://wiki.acfun.tv/index.php/" + t;
            }
        });
        Pattern http = Pattern.compile("(http://(?:[a-z0-9.-]+[.][a-z]{2,}+(?::[0-9]+)?)(?:/\\S*)?)",
                Pattern.CASE_INSENSITIVE);
        Linkify.addLinks(text, http, "http://");
        Linkify.addLinks(text, Pattern.compile("(ac\\d{5,})", Pattern.CASE_INSENSITIVE), "av://");
    }

    private Intent createShareIntent() {
        String shareurl = title + "http://www.acfun.tv/v/ac" + aid;
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "分享");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareurl);
        return shareIntent;
    }

    public void onResume() {
        super.onResume();
        MobclickAgent.onResume(this);
    }

    public void onPause() {
        super.onPause();
        MobclickAgent.onPause(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unbindService(conn);
        } catch (Exception e) {}
    }
}
