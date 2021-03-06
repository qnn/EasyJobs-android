package com.cghio.easyjobs;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Parameters extends EasyJobsBase {

    private static String API_TOKEN = "";
    private static String JOBS_PARAMETERS_INDEX_URL = "";
    private static String PARAM = "";
    private static String DEFAULT = "";

    private static List<Map<String, Object>> data;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parameters);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            if (extras.containsKey("JOBS_PARAMETERS_INDEX_URL")) {
                JOBS_PARAMETERS_INDEX_URL = extras.getString("JOBS_PARAMETERS_INDEX_URL");
            }
            if (extras.containsKey("PARAM")) {
                PARAM = extras.getString("PARAM");
                if (PARAM != null && PARAM.length() > 0) {
                    setTitle(String.format(getString(R.string.parameter_x), PARAM));
                }
            }
            if (extras.containsKey("API_TOKEN")) {
                API_TOKEN = extras.getString("API_TOKEN");
            }
            if (extras.containsKey("DEFAULT")) {
                DEFAULT = extras.getString("DEFAULT");
            }
        }
        getParams(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.reload_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.reload:
                getParams(true);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void initAdapter() {
        data = new ArrayList<Map<String, Object>>();

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("V", getString(R.string.custom_value_));
        data.add(map);
    }

    private void setAdapter() {
        SimpleAdapter adapter = new SimpleAdapter(Parameters.this, data,
                R.layout.listview_job_parameters_items, new String[]{"V"},
                new int[]{R.id.text_param});
        ListView listview_job_parameters =
                (ListView) findViewById(R.id.listview_job_parameters);
        listview_job_parameters.setAdapter(adapter);
        adapter.notifyDataSetChanged();
    }

    private void getParams(boolean forceUpdate) {
        initAdapter();
        setAdapter();

        String cachedContent = getEtagContent(JOBS_PARAMETERS_INDEX_URL);
        if (cachedContent.length() > 0) {
            parseContent(cachedContent);
        }

        if (JOBS_PARAMETERS_INDEX_URL.length() == 0 || PARAM.length() == 0) return;

        RequestParams params = new RequestParams();
        params.put("token", API_TOKEN);
        AsyncHttpClient client = new AsyncHttpClient();
        client.setTimeout(TIMEOUT);
        showLoading();

        if (!forceUpdate)
            client.addHeader(IF_NONE_MATCH, getEtag(JOBS_PARAMETERS_INDEX_URL));

        client.get(JOBS_PARAMETERS_INDEX_URL, params, new AsyncHttpResponseHandler() {
            @Override
            public void onFinish() {
                hideLoading();
            }
            @Override
            public void onFailure(Throwable e, String response) {
                if (isNotModified(e)) return;
                if (e != null && e.getCause() != null) {
                    showSimpleErrorDialog(e.getCause().getMessage());
                } else if (e != null && e.getCause() == null) {
                    showSimpleErrorDialog(e.getMessage());
                } else {
                    showSimpleErrorDialog(getString(R.string.error_connection_problem));
                }
                showReloadButton();
            }
            @Override
            public void onSuccess(int statusCode, Header[] headers, String content) {
                String etag = getHeader(headers, ETAG);
                saveETagAndContent(JOBS_PARAMETERS_INDEX_URL, etag, content);
                parseContent(content);
            }
        });
    }

    private void parseContent(String content) {
        try {
            // if data is null or data has more than one item (meaning list loaded before)
            if (data == null || data.size() > 1) initAdapter();

            JSONObject job_parameters = new JSONObject(content);
            if (job_parameters.has(PARAM)) {
                JSONArray params = job_parameters.getJSONArray(PARAM);
                for (int i = 0; i < params.length(); i++) {
                    Map<String, Object> map = new HashMap<String, Object>();
                    map.put("V", params.getString(i));
                    data.add(map);
                }
            }

            ListView listview_job_parameters =
                    (ListView) findViewById(R.id.listview_job_parameters);
            listview_job_parameters.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i == 0) {
                        showCustomInput();
                    } else {
                        Object item = adapterView.getAdapter().getItem(i);
                        if (item instanceof Map) {
                            sendBackData(((Map) item).get("V").toString());
                        }
                    }
                }
            });
            listview_job_parameters.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    Object item = adapterView.getAdapter().getItem(i);
                    if (item instanceof Map) {
                        if (((Map) item).containsKey("V")) {
                            copyText(((Map) item).get("V").toString());
                            Toast.makeText(Parameters.this, R.string.string_copied, Toast.LENGTH_SHORT).show();
                            return true;
                        }
                    }
                    return false;
                }
            });

            setAdapter();
        } catch (JSONException e) {
            showSimpleErrorDialog(getString(R.string.error_should_update_easyjobs));
            showReloadButton();
        }
    }

    private void sendBackData(String data) {
        Intent intent = new Intent();
        intent.putExtra("key", PARAM);
        intent.putExtra("value", data);
        Parameters.this.setResult(RESULT_OK, intent);
        Parameters.this.finish();
    }

    private void showCustomInput() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(getString(R.string.custom_value));
        final EditText input = new EditText(this);
        input.setSingleLine();
        input.setText(DEFAULT);
        alert.setView(input);
        alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                Editable editable = input.getText();
                if (editable != null) {
                    String value = editable.toString().trim();
                    sendBackData(value);
                }
            }
        });
        alert.setNegativeButton(R.string.cancel, null);
        alert.show();
    }

    private void showReloadButton() {
        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("K", getString(R.string.retry));
        map.put("V", getString(R.string.retry_desc));
        data.add(map);
        SimpleAdapter adapter = new SimpleAdapter(Parameters.this, data,
                R.layout.listview_jobs_items, new String[]{"K", "V"},
                new int[]{R.id.text_key, R.id.text_value});
        ListView listview_job_parameters = (ListView) findViewById(R.id.listview_job_parameters);
        listview_job_parameters.setAdapter(adapter);
        listview_job_parameters.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                getParams(true);
            }
        });
    }

}
