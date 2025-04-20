package com.example.bookworm;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChartPagerAdapter extends RecyclerView.Adapter<ChartPagerAdapter.ChartViewHolder> {

    private final Context context;
    private final List<Integer> years;
    private final Map<Integer, Map<Integer, Integer>> yearMonthBookCountMap;
    private final String[] months = {"Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"};

    public ChartPagerAdapter(Context context, List<Integer> years, Map<Integer, Map<Integer, Integer>> yearMonthBookCountMap) {
        this.context = context;
        this.years = years;
        this.yearMonthBookCountMap = yearMonthBookCountMap;
    }

    @NonNull
    @Override
    public ChartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_chart, parent, false);
        return new ChartViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChartViewHolder holder, int position) {
        int year = years.get(position);
        Map<Integer, Integer> monthCountMap = yearMonthBookCountMap.get(year);
        
        setupBarChart(holder.barChart, monthCountMap, year);
    }

    @Override
    public int getItemCount() {
        return years.size();
    }

    private void setupBarChart(BarChart barChart, Map<Integer, Integer> monthCountMap, int year) {
        List<BarEntry> entries = new ArrayList<>();
        int maxValue = 0;
        
        // Create bar entries for each month
        for (int i = 1; i <= 12; i++) {
            float value = monthCountMap.get(i) != null ? monthCountMap.get(i) : 0;
            entries.add(new BarEntry(i - 1, value));
            maxValue = Math.max(maxValue, (int) value);
        }
        
        // Get theme colors
        int accentColor = context.getResources().getColor(R.color.accent);
        
        // Check if using night mode to determine text color
        boolean isNightMode = (context.getResources().getConfiguration().uiMode 
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) 
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        
        int textColor = isNightMode ? Color.WHITE : Color.BLACK;
        
        // Create dataset
        BarDataSet dataSet = new BarDataSet(entries, "Количество книг");
        dataSet.setColor(accentColor);
        dataSet.setValueTextColor(textColor);
        dataSet.setValueTextSize(10f);
        
        // Create bar data
        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.7f);
        
        // Setup chart
        barChart.setData(barData);
        barChart.setFitBars(true);
        barChart.setDrawGridBackground(false);
        barChart.setDrawBorders(false);
        barChart.setDrawValueAboveBar(true);
        
        // Remove description
        Description description = new Description();
        description.setText("");
        barChart.setDescription(description);
        
        // Setup X axis
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(months));
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(textColor);
        
        // Setup Y axis
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setGranularity(1f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setTextColor(textColor);
        
        // Если максимальное значение меньше 5, устанавливаем верхний предел 5
        if (maxValue < 5) {
            leftAxis.setAxisMaximum(5f);
        } else {
            // Округляем до ближайшего большего кратного 5
            leftAxis.setAxisMaximum(((maxValue / 5) + 1) * 5);
        }
        
        barChart.getAxisRight().setEnabled(false);
        barChart.getLegend().setTextColor(textColor);
        
        // Animate chart
        barChart.animateY(500);
        
        // Set value selection listener
        barChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(com.github.mikephil.charting.data.Entry e, Highlight h) {
                int month = (int) e.getX() + 1; // Индекс месяца начинается с 0
                int count = (int) e.getY();
                String monthName = getMonthName(month);
                
                String message = String.format("В %s %d вы прочли %d %s", 
                    monthName, year, count, getBooksCountString(count));
                
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected() {
                // Do nothing
            }
        });
        
        barChart.invalidate();
    }
    
    private String getMonthName(int month) {
        String[] fullMonths = {"январе", "феврале", "марте", "апреле", "мае", "июне", 
                              "июле", "августе", "сентябре", "октябре", "ноябре", "декабре"};
        return fullMonths[month - 1];
    }
    
    private String getBooksCountString(int count) {
        if (count % 10 == 1 && count % 100 != 11) {
            return "книгу";
        } else if ((count % 10 == 2 || count % 10 == 3 || count % 10 == 4) && 
                  (count % 100 != 12 && count % 100 != 13 && count % 100 != 14)) {
            return "книги";
        } else {
            return "книг";
        }
    }

    static class ChartViewHolder extends RecyclerView.ViewHolder {
        BarChart barChart;

        public ChartViewHolder(@NonNull View itemView) {
            super(itemView);
            barChart = itemView.findViewById(R.id.barChart);
        }
    }
} 