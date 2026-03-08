package com.example.germanspreach.data;

import android.content.Context;
import com.example.germanspreach.models.PhraseItem;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PhraseProvider {
    public static Map<String, List<PhraseItem>> getPhrasesByTopic(Context context) {
        Map<String, List<PhraseItem>> data = new LinkedHashMap<>();
        
        try {
            String[] files = context.getAssets().list("data");
            if (files != null) {
                for (String file : files) {
                    if (file.endsWith(".json")) {
                        loadFromAsset(context, "data/" + file, data);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return data;
    }

    private static void loadFromAsset(Context context, String fileName, Map<String, List<PhraseItem>> data) {
        try {
            InputStream is = context.getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);
            
            JSONObject root = new JSONObject(json);
            JSONArray topics = root.names();
            if (topics == null) return;

            for (int i = 0; i < topics.length(); i++) {
                String topic = topics.getString(i);
                JSONArray items = root.getJSONArray(topic);
                List<PhraseItem> list = data.getOrDefault(topic, new ArrayList<>());
                
                for (int j = 0; j < items.length(); j++) {
                    JSONObject obj = items.getJSONObject(j);
                    JSONArray phrasesDeArr = obj.getJSONArray("phrasesDe");
                    JSONArray phrasesUkArr = obj.getJSONArray("phrasesUk");
                    
                    List<String> pDe = new ArrayList<>();
                    List<String> pUk = new ArrayList<>();
                    for (int k = 0; k < phrasesDeArr.length(); k++) pDe.add(phrasesDeArr.getString(k));
                    for (int k = 0; k < phrasesUkArr.length(); k++) pUk.add(phrasesUkArr.getString(k));

                    list.add(new PhraseItem(
                        obj.getString("wordDe"),
                        obj.getString("wordUk"),
                        pDe,
                        pUk
                    ));
                }
                data.put(topic, list);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
