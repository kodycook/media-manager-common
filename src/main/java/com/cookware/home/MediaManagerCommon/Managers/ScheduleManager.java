package com.cookware.home.MediaManagerCommon.Managers;

import com.cookware.home.MediaManagerCommon.DataTypes.Config;
import com.cookware.common.Tools.CsvTools;
import org.apache.log4j.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Created by Kody on 12/10/2017.
 */
public class ScheduleManager {
    // TODO: Modify scheduler so that config can be modified on the go
    private static final Logger log = Logger.getLogger(ScheduleManager.class);
    private final Config config;
    private final CsvTools csvTools = new CsvTools();
    private final autoDownload downloading;
    private final int daysInAWeek = 7;
    private final int hoursInADay = 24;
    private boolean[][] weekSchedule = new boolean[this.hoursInADay][this.daysInAWeek];
    private boolean autoDownloadState = false;

    public ScheduleManager(Config mConfig){
        config = mConfig;
        String state = config.schedulerState;

        if (state.equals("ON") || state.equals("on") || state.equals("On")){
            downloading = autoDownload.MANUAL_ON;
            log.info("Scheduler set to \"ON\"");
        }
        else if (state.equals("OFF") || state.equals("off") || state.equals("Off")){
            downloading = autoDownload.MANUAL_OFF;
            log.info("Scheduler set to \"OFF\"");
        }
        else {
            downloading = autoDownload.AUTOMATIC;
            log.info("Scheduler set to \"AUTOMATIC\"");
        }

        if(csvTools.createFile(this.config.scheduleFileName)){
            initialiseSchedule();
            saveSchedule();
        }
        else {
            loadSchedule();
//            System.out.println(toString());
        }
    }

    private void initialiseSchedule(){
        for(int hour = 0; hour < this.hoursInADay; hour++){
            for(int day = 0; day < this.daysInAWeek; day++){
                this.weekSchedule[hour][day] = true;
            }
        }
    }

    public boolean isDownloading() {
        if(downloading.equals(autoDownload.MANUAL_ON)) {
            return true;
        }
        else if(downloading.equals(autoDownload.MANUAL_OFF)) {
            return false;
        }
        else {
            return isCurrentlyScheduledForDownload();
        }
    }

    private boolean isCurrentlyScheduledForDownload() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        boolean currentlyDownloading;


        int hour = now.getHour();
        int day = now.getDayOfWeek().getValue() - 1;

        currentlyDownloading = weekSchedule[hour][day];

        if(currentlyDownloading & !this.autoDownloadState){
            log.info("ScheduleManager started downloader");
        }
        if(!currentlyDownloading & this.autoDownloadState){
            log.info("ScheduleManager stopped downloader");
        }
        this.autoDownloadState = currentlyDownloading;

        return currentlyDownloading;
    }


    public int getScheduleState(){
        if(this.downloading.equals(autoDownload.MANUAL_OFF)){
            return 0;
        }
        else if (this.downloading.equals(autoDownload.MANUAL_ON)){
            return 1;
        }
        else {
            return 2;
        }
    }


    private void saveSchedule(){
        csvTools.writeStringArrayToCsv(this.config.scheduleFileName, convertScheduleToStringArray());
    }


    private void loadSchedule(){
        String savedConfig[][] = csvTools.getStringArrayFromCsv(this.config.scheduleFileName);
        convertStringArrayToSchedule(savedConfig);
    }


    private String[][] convertScheduleToStringArray(){
        String [][] result = new String[this.hoursInADay + 1][this.daysInAWeek + 1];
        result[0] = new String[]{"",
                "Monday",
                "Tuesday",
                "Wednesday",
                "Thursday",
                "Friday",
                "Saturday",
                "Sunday"
        };

        for(int hour = 0; hour < this.hoursInADay; hour++){
            result[hour+1][0] = String.format("%02d:00 - %02d:59", hour, hour);
            for(int day = 0; day < this.daysInAWeek; day++){
                result[hour+1][day+1] = this.weekSchedule[hour][day] ? "x" : "";
            }
        }
        return result;
    }


    private void convertStringArrayToSchedule(String[][] savedConfig){
        for(int hour = 0; hour < this.hoursInADay; hour++){
            for(int day = 0; day < this.daysInAWeek; day++){
                if ((savedConfig[hour + 1][day + 1].equals("x")) || (savedConfig[hour + 1][day + 1].equals("x\r")))
                    this.weekSchedule[hour][day] = true;
                else {
                    this.weekSchedule[hour][day] = false;
                }
            }
        }
    }

    public String toString(){
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(String.format("\t\t\t\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
                "Monday   ",
                "Tuesday  ",
                "Wednesday",
                "Thursday ",
                "Friday   ",
                "Saturday ",
                "Sunday   "));


        for(int hour = 0; hour < this.hoursInADay; hour++){
            stringBuilder.append(String.format("\n%02d:00 - %02d:59\t", hour, hour));
            for(int day = 0; day < this.daysInAWeek; day++){
                stringBuilder.append(String.format("%s\t\t\t", this.weekSchedule[hour][day] ? "x" : ""));
            }
        }

        return stringBuilder.toString();
    }

    public enum autoDownload{
        AUTOMATIC, MANUAL_ON, MANUAL_OFF
    }
}
