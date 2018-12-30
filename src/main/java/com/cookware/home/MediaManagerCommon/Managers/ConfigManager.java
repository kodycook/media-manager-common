package com.cookware.home.MediaManagerCommon.Managers;

import com.cookware.home.MediaManagerCommon.DataTypes.Config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Created by Kody on 30/10/2017.
 */
public class ConfigManager {
//    private static final Logger log = Logger.getLogger(ConfigManager.class); // Cannot be used until logs are instanciated
    Config config = new Config();

    public ConfigManager(String path){
        Properties properties = new Properties();
        InputStream input = null;

        try {

            input = new FileInputStream(path);
            properties.load(input);

            config.logPropertiesPath = properties.getProperty("logProperties").replaceAll("\\s","");
            config.logsPath = properties.getProperty("logs").replaceAll("\\s","");
            config.databasePath = properties.getProperty("database").replaceAll("\\s","");
            config.scheduleFileName = properties.getProperty("schedule").replaceAll("\\s","");
            config.tempPath = properties.getProperty("tempMedia").replaceAll("\\s","");
            config.finalPath = properties.getProperty("finalMedia").replaceAll("\\s","");
            config.schedulerState = properties.getProperty("schedulerState").replaceAll("\\s","");
            config.mediaSite = properties.getProperty("site").replaceAll("\\s","");

        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public Config getConfig(){
        return this.config;
    }
}
