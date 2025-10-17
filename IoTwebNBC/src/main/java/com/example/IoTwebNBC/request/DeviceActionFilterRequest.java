package com.example.IoTwebNBC.request;



public class DeviceActionFilterRequest {
    private String room;
    private String device;
    private String status;
    private String inputSearch;

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getInputSearch() {
        return inputSearch;
    }

    public void setInputSearch(String inputSearch) {
        this.inputSearch = inputSearch;
    }


}
