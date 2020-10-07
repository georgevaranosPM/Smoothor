package com.reborntales.smoothor;
//This class is used for Adapting the inforamtion from the database to the List View

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RouteAdapter extends ArrayAdapter<Route> {

    private final LayoutInflater inflater;
    private final int layoutResource;
    private final ArrayList<Route> routes;

    public RouteAdapter(Context context,
                          int resource,
                          ArrayList<Route> objects) {
        super(context, resource, objects);

        inflater = LayoutInflater.from(context);
        layoutResource = resource;
        routes = objects;
    }

    @Override
    public int getCount() {
        return routes.size();
    }

    @NonNull
    @Override
    public View getView(final int position,
                        @Nullable final View convertView,
                        @NonNull ViewGroup parent) {

        @SuppressLint("ViewHolder") final View view = inflater.inflate(layoutResource,
                parent,
                false);

        TextView routeName = view.findViewById(R.id.nameTextView);
        TextView routeDate = view.findViewById(R.id.dateTextView);

        routeName.setText(routes.get(position).getRouteName());
        routeDate.setText(routes.get(position).getRouteDate());

        return view;
    }
}
