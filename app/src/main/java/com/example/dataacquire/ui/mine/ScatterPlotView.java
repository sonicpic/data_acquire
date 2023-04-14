package com.example.dataacquire.ui.mine;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.example.dataacquire.entity.Item;

import java.util.List;

public class ScatterPlotView extends View {
    private List<Item> itemList;

    public ScatterPlotView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setItemList(List<Item> itemList) {
        this.itemList = itemList;
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (itemList != null) {
            // Get view size
            int width = getWidth();
            int height = getHeight();

            // Find minimum and maximum longitude and latitude values
            double minLongitude = Double.MAX_VALUE;
            double maxLongitude = Double.MIN_VALUE;
            double minLatitude = Double.MAX_VALUE;
            double maxLatitude = Double.MIN_VALUE;

            for (Item item : itemList) {
                if (item.getLongitude() < minLongitude) {
                    minLongitude = item.getLongitude();
                }
                if (item.getLongitude() > maxLongitude) {
                    maxLongitude = item.getLongitude();
                }
                if (item.getLatitude() < minLatitude) {
                    minLatitude = item.getLatitude();
                }
                if (item.getLatitude() > maxLatitude) {
                    maxLatitude = item.getLatitude();
                }
            }

            // Calculate longitude and latitude
            double longitudeRange = maxLongitude - minLongitude;
            double latitudeRange = maxLatitude - minLatitude;

            // Calculate GRI range
            double minGri = Double.MAX_VALUE;
            double maxGri = Double.MIN_VALUE;

            for (Item item : itemList) {
                if (item.getGri() < minGri) {
                    minGri = item.getGri();
                }
                if (item.getGri() > maxGri) {
                    maxGri = item.getGri();
                }
            }

            double griRange = maxGri - minGri;

            // Draw each data point
            Paint paint = new Paint();
            for (Item item : itemList) {
                // Calculate x and y position
                float x = (float) ((item.getLongitude() - minLongitude) / longitudeRange * (width - 100) + 50);
//                    float y = (float) ((item.getLatitude() - minLatitude) / latitudeRange * (height-100)+50);
                float y = (float) ((maxLatitude - item.getLatitude()) / latitudeRange * (height - 100) + 50);


                // Calculate color based on GRI value
                float hue = (float) (1 - (item.getGri() - minGri) / griRange) * 60;
                int color = Color.HSVToColor(new float[]{
                        hue,
                        1,
                        1
                });

                // Set paint color
                paint.setColor(color);

                // Draw data point
                canvas.drawCircle(x, y, 10, paint);
            }
        }
    }
}

