package com.invent.ontocook.multiple_connection;

import android.content.Context;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.SectionIndexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ListAdapter extends ArrayAdapter<String> implements SectionIndexer {

    String[] sections;
    List<String> fruits;
    List<String> sectionLetters = new ArrayList<String>();

    public ListAdapter(Context context, List<String> fruitList) {
        super(context, android.R.layout.simple_list_item_1, fruitList);
        this.fruits = fruitList;

        for (int x = 0; x < fruits.size(); x++) {
            String fruit = fruits.get(x);
            String ch = fruit.charAt(0) + "";
            ch = ch.toUpperCase(Locale.US);
            sectionLetters.add(ch);
        }
        Log.e("FastScroll", "ListAdapter: Fruit" + fruitList.size());
        Log.e("FastScroll", "ListAdapter: " + sectionLetters.size());
        ArrayList<String> sectionList = new ArrayList<String>(sectionLetters);

        sections = new String[sectionList.size()];
        Log.e("FastScroll", "ListAdapter: " + sections.length);

        sectionList.toArray(sections);
    }

    public int getPositionForSection(int section) {

        Log.e("sushildlh", "" + section);
        return section;
    }

    public int getSectionForPosition(int position) {

        Log.d("sushildlh", "" + position);
        return position;
    }

    public Object[] getSections() {
        return sections;
    }
}