package com.example.IoTwebNBC.request;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class DataSensorFilterRequest {
    private String room;
    private List<String> sensors;
    private Map<String, String> thresholdOps; // lưu loại ngưỡng được chọn: light: < , temp: >=
    private Map<String, String> thresholdValues; // lưu giá trị ngưỡng: light: 100, temp: 50-40
    private String inputSearch; // dữ liệu số người dùng nhập vào
    private String describeSearch; // mô tả lại những gì ngường dùng search

    public String getRoom() {
        return room;
    }

    public List<String> getSensors() {
        return sensors;
    }

    public Map<String, String> getThresholdOps() {
        return thresholdOps;
    }

    public Map<String, String> getThresholdValues() {
        return thresholdValues;
    }

    public String getInputSearch() {
        return inputSearch;
    }

    public String getDescribeSearch() {
        return describeSearch;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public void setSensors(List<String> sensors) {
        this.sensors = sensors;
    }

    public void setThresholdOps(Map<String, String> thresholdOps) {
        this.thresholdOps = thresholdOps;
    }

    public void setThresholdValues(Map<String, String> thresholdValues) {
        this.thresholdValues = thresholdValues;
    }

    public void setInputSearch(String inputSearch) {
        this.inputSearch = inputSearch;
    }

    public void setDescribeSearch(String describeSearch) {
        this.describeSearch = describeSearch;
    }
}
