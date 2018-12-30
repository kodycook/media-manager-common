package com.cookware.home.MediaManagerCommon.Managers;

import com.cookware.home.MediaManagerCommon.DataTypes.DownloadState;
import com.cookware.home.MediaManagerCommon.DataTypes.MediaInfo;
import com.cookware.home.MediaManagerCommon.DataTypes.MediaType;
import com.cookware.common.Tools.DirectoryTools;
import com.cookware.home.MediaManagerCommon.Tools.FileNameTools;
import org.apache.log4j.Logger;

import java.math.BigInteger;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Kody on 8/09/2017.
 */
public class DatabaseManager {
    private FileNameTools fileNameTools = new FileNameTools();
    private DirectoryTools directoryTools = new DirectoryTools();
    private final Logger log = Logger.getLogger(DatabaseManager.class);
    private final List<DatabaseEntryAttribute> databaseAttributes  = new ArrayList<DatabaseEntryAttribute>();
    private String databasePath;
    private String driverPath;

    public DatabaseManager(String mFilePath) {
        this.databasePath = mFilePath;
        this.driverPath = "jdbc:sqlite:" + this.databasePath + "/media.db";
    }

    public void initialise() {
        // TODO: Re-sort Database entries to make it easier to read
        databaseAttributes.add(new DatabaseEntryAttribute("ID", "BIGINT NOT NULL", BigInteger.class));
        databaseAttributes.add(new DatabaseEntryAttribute("NAME", "TEXT NOT NULL", String.class));
        databaseAttributes.add(new DatabaseEntryAttribute("TYPE", "TINYINT NOT NULL", Integer.class));
        databaseAttributes.add(new DatabaseEntryAttribute("URL", "TEXT NOT NULL", String.class));
        databaseAttributes.add(new DatabaseEntryAttribute("QUALITY", "SMALLINT NOT NULL", Integer.class));
        databaseAttributes.add(new DatabaseEntryAttribute("STATE", "TINYINT NOT NULL", Integer.class));
        databaseAttributes.add(new DatabaseEntryAttribute("PRIORITY", "TINYINT NOT NULL", Integer.class));
        databaseAttributes.add(new DatabaseEntryAttribute("RELEASED", "DATE NOT NULL", LocalDate.class));
        databaseAttributes.add(new DatabaseEntryAttribute("ADDED", "DATE NOT NULL", LocalDate.class));
        databaseAttributes.add(new DatabaseEntryAttribute("DOWNLOADED", "DATE", LocalDate.class));
        databaseAttributes.add(new DatabaseEntryAttribute("PATH", "TEXT", String.class));
        databaseAttributes.add(new DatabaseEntryAttribute("PARENTID", "BIGINT", BigInteger.class));
        databaseAttributes.add(new DatabaseEntryAttribute("PARENTNAME", "TEXT", String.class));
        databaseAttributes.add(new DatabaseEntryAttribute("EPISODE", "TEXT", float.class));

        directoryTools.createNewDirectory(databasePath);

        initialiseDatabase();
    }


    public List<MediaInfo> getDownloadQueue() {
        return getMediaItemsWithState(DownloadState.PENDING);
    }


    public List<MediaInfo> getMediaItemsWithState(DownloadState downloadState){
        return getAllDataBaseEntriesWithMatchedCriteria("STATE", downloadState.ordinal());
    }


    public void updateState(BigInteger mediaId, DownloadState downloadState){

        updateDatabaseValue(mediaId, "STATE", downloadState.ordinal());
    }


    public void updatePath(BigInteger mediaId, String path){

        updateDatabaseValue(mediaId, "PATH", fileNameTools.removeSpecialCharactersFromPath(path));
    }


    public void updateQuality(BigInteger mediaId, int quality){

        updateDatabaseValue(mediaId, "QUALITY", quality);
    }


    public void updatePriority(BigInteger mediaId, int priority){
        updateDatabaseValue(mediaId, "PRIORITY", priority);
    }


    public void updateDownloadDate(BigInteger id, LocalDate date){

        updateDatabaseValue(id, "DOWNLOADED", date);
    }


    private void initialiseDatabase(){
        boolean querySucceeded = false;

        if(!checkIfDatabaseExists()) {
            String sql = "CREATE TABLE MEDIA(";
            for (DatabaseEntryAttribute currentDatabaseEntryAttribute : databaseAttributes) {
                sql += String.format("%s %s, ", currentDatabaseEntryAttribute.NAME, currentDatabaseEntryAttribute.INITPARAM);
            }
            sql += String.format("PRIMARY KEY (%s))", databaseAttributes.get(0).NAME);

            querySucceeded = sendSqlQuery(sql);

            if (!querySucceeded) {
                log.error("Failed to initialise Database");
                log.error("shutting down because data base could not be initialised");
                System.exit(1);
            }
            log.error("Created new Database");
        }
        log.info("Successfully opened database");
    }


    public boolean addMediaToDatabase(MediaInfo info) {
        final DownloadState state = DownloadState.PENDING;
        final LocalDate added = LocalDate.now();

        if (checkIfMediaExists(info.ID)) {
            MediaInfo clashedItem = getMediaItemWithMatchedId(info.ID);
            if (clashedItem == null) {
                log.error("UNKNOWN ERROR");
            }
            log.debug(String.format("Hash collision while trying to write to Database between:\n%s\nAND\n%s",
                    info.toString(),
                    clashedItem.toString()));
            return false;
        }

        String sql;
        if (info.TYPE.equals(MediaType.EPISODE)) {
            sql = String.format("INSERT INTO MEDIA (ID,NAME,TYPE,URL,QUALITY,STATE,PRIORITY,RELEASED,ADDED,EPISODE,PARENTID,PARENTNAME) " +
                            "VALUES (%d, '%s', %d, '%s', %d, %d, %d, '%s', '%s', %.2f, %d, '%s');",
                    info.ID,
                    fileNameTools.removeSpecialCharactersFromFileName(info.NAME).replace("'", "''"),
                    info.TYPE.ordinal(),
                    info.URL,
                    info.QUALITY,
                    state.ordinal(),
                    info.PRIORITY,
                    java.sql.Date.valueOf(info.RELEASED),
                    java.sql.Date.valueOf(added),
                    info.EPISODE,
                    info.PARENTSHOWID,
                    fileNameTools.removeSpecialCharactersFromFileName(info.PARENTSHOWNAME).replace("'", "''"));
        } else {
            sql = String.format("INSERT INTO MEDIA (ID,NAME,TYPE,URL,QUALITY,STATE,PRIORITY,RELEASED,ADDED) " +
                            "VALUES (%d, '%s', %d, '%s', %d, %d, %d, '%s', '%s');",
                    info.ID,
                    fileNameTools.removeSpecialCharactersFromFileName(info.NAME).replace("'", "''"),
                    info.TYPE.ordinal(),
                    info.URL,
                    info.QUALITY,
                    state.ordinal(),
                    info.PRIORITY,
                    java.sql.Date.valueOf(info.RELEASED),
                    java.sql.Date.valueOf(added));
        }

        return sendSqlQuery(sql);
    }


    public void updateDatabaseValue(BigInteger id, String key, Object value){
        DatabaseEntryAttribute associatedDatabaseEntryAttribute = getDatabaseAttributeByName(key);
        String databaseAttributeValueString = getDatabaseAttributeValueAsString(associatedDatabaseEntryAttribute, value);

        // ASK WILL IS IT MORE IMPORTANT TO INITIALISE VARIABLES AS FINAL OR TO DECLARE THEM AT THE START OF A METHOD (SOMETIMES YOU CANT DO BOTH)
        final String sql = String.format("UPDATE MEDIA SET %s = %s where ID = %d ;",
                associatedDatabaseEntryAttribute.NAME,
                databaseAttributeValueString,
                id);

        final boolean querySucceeded = sendSqlQuery(sql);

        if (!querySucceeded) {
            log.error(String.format("Failed to update Database ID: %d Attribute: %s Value: %s",id, key, databaseAttributeValueString));
            log.error("Shutting down due to lack of database access");
            System.exit(1);
        }
    }


    // TODO: Finish this (updateEntireDatabaseEntry) method
    public void updateEntireDatabaseEntry(MediaInfo mediaInfo){
    }


    public Object getMediaItemValue(BigInteger id, String key){
        String query = String.format("SELECT %s " +
                "FROM MEDIA " +
                "WHERE ID = %d ;",key, id);

        List<MediaInfo> mediaInfo = receiveSqlRequest(query);

        return getDataBaseObjectFromMediaInfo(mediaInfo.get(0), key);
    }


    public MediaInfo getMediaItemWithMatchedId(BigInteger id){
        List<MediaInfo> mediaInfoList;

        String query = String.format("SELECT * " +
                        "FROM MEDIA " +
                        "WHERE ID = %d ;",id);

        mediaInfoList = receiveSqlRequest(query);

        if(mediaInfoList.isEmpty()){
            log.error(String.format("Media Item ID: %d not in database", id));
            return null;
        }

        return mediaInfoList.get(0);
    }


    public List<MediaInfo> getAllDataBaseEntriesWithMatchedCriteria(String key, Object value){
        final List<MediaInfo> matchedMediaInfo = new ArrayList<MediaInfo>();
        final DatabaseEntryAttribute associatedDatabaseEntryAttribute = getDatabaseAttributeByName(key);
        final String databaseAttributeValueString = getDatabaseAttributeValueAsString(associatedDatabaseEntryAttribute, value);
        List<MediaInfo> result = null;

        if(associatedDatabaseEntryAttribute != null) {
            final String query = String.format("SELECT * " +
                            "FROM MEDIA " +
                            "WHERE %s = %s",
                    associatedDatabaseEntryAttribute.NAME,
                    databaseAttributeValueString);

            result = receiveSqlRequest(query);
        }
        return result;
    }


    private DatabaseEntryAttribute getDatabaseAttributeByName(String name){
        DatabaseEntryAttribute associatedDatebaseEntryAttribute;
        for(DatabaseEntryAttribute currentDatabaseEntryAttribute: databaseAttributes){
            if (currentDatabaseEntryAttribute.NAME.equals(name)){
                associatedDatebaseEntryAttribute = currentDatabaseEntryAttribute;
                return associatedDatebaseEntryAttribute;
            }
        }
        log.error(String.format("No database entry attribute found for %s", name));
        return null;
    }


    public Object getDataBaseObjectFromMediaInfo(MediaInfo mediaInfo, String name){

        if (name.equals("ID")){
            return mediaInfo.ID;
        }
        if (name.equals("NAME")){
            return mediaInfo.NAME;
        }
        if (name.equals("TYPE")){
            return mediaInfo.TYPE;
        }
        if (name.equals("URL")){
            return mediaInfo.URL;
        }
        if (name.equals("QUALITY")){
            return mediaInfo.QUALITY;
        }
        if (name.equals("STATE")){
            return mediaInfo.STATE;
        }
        if (name.equals("PRIORITY")){
            return mediaInfo.PRIORITY;
        }
        if (name.equals("RELEASED")){
            return mediaInfo.RELEASED;
        }
        if (name.equals("ADDED")){
            return mediaInfo.ADDED;
        }
        if (name.equals("PATH")){
            return mediaInfo.PATH;
        }
        if (name.equals("PARENTID")){
            return mediaInfo.PARENTSHOWID;
        }
        if (name.equals("PARENTNAME")){
            return mediaInfo.PARENTSHOWNAME;
        }
        if (name.equals("EPISODE")){
            return mediaInfo.EPISODE;
        }
        log.error(String.format("Could not find MediaInfo attribute related to '%s'", name));
        return null;
    }


    private String getDatabaseAttributeValueAsString(DatabaseEntryAttribute associatedDatabaseEntryAttribute, Object value) {
        String databaseAttributeValueAsString = null;
        try {
            if (associatedDatabaseEntryAttribute.DATATYPE.equals(String.class)) {
                return String.format("'%s'", ((String) value).replace("'", "''"));
            }
            if (associatedDatabaseEntryAttribute.DATATYPE.equals(BigInteger.class)) {
                return String.format("%d", (BigInteger) value);
            }
            if (associatedDatabaseEntryAttribute.DATATYPE.equals(Integer.class)) {
                return String.format("%d", (Integer) value);
            }
            if (associatedDatabaseEntryAttribute.DATATYPE.equals(LocalDate.class)) {
                return String.format("'%s'", Date.valueOf((LocalDate) value));
            }
            if (associatedDatabaseEntryAttribute.DATATYPE.equals(float.class)) {
                return String.format("%02f", (float) value);
            }
        }
        catch (ClassCastException e){
            log.error("Failed to cast value", e);
        }
        return null;
    }


    private synchronized boolean checkIfDatabaseExists(){
        Connection conn = null;
        boolean exists = false;
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(this.driverPath);

            DatabaseMetaData meta = conn.getMetaData();
            ResultSet res = meta.getTables(null, null, "MEDIA",
                    new String[] {"TABLE"});

            if(res.next()){
                exists = true;
            }
            else{
                exists = false;
            }

            conn.close();

        } catch (Exception e) {
            log.error("Issue checking if Database exists", e);
            log.error("Shutting down due to lack of database access");
            System.exit(1);
        }
        return exists;
    }


    private boolean checkIfMediaExists(BigInteger id) {
        final String query = String.format("SELECT * FROM MEDIA WHERE ID = %d ;", id);
        final List<MediaInfo> mediaItems;

        mediaItems = receiveSqlRequest(query);

        if(mediaItems.isEmpty()){
            return false;
        }
        else {
            return true;
        }
    }


    private synchronized boolean sendSqlQuery(String query){
        Connection conn = null;
        Statement stmt = null;

        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(this.driverPath);
            conn.setAutoCommit(false);

            log.debug(String.format("SQL sent to Database: \"%s\"", query));

            stmt = conn.createStatement();
            stmt.executeUpdate(query);
            conn.commit();

            stmt.close();
            conn.close();

        } catch (Exception e) {
            log.error(e);
            return false;
        }
        return true;
    }


    private synchronized List<MediaInfo> receiveSqlRequest(String query) {
        List<MediaInfo> extractedMediaInfo = new ArrayList<MediaInfo>();
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(this.driverPath);
            conn.setAutoCommit(false);

            log.debug(String.format("SQL sent to Database: \"%s\"", query));

            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);
            conn.commit();

            while (rs.next()) {
                MediaInfo mediaInfo = new MediaInfo();
                mediaInfo.ID = new BigInteger(rs.getString("ID"));
                mediaInfo.NAME = rs.getString("NAME");
                mediaInfo.TYPE = MediaType.values()[rs.getInt("TYPE")];
                mediaInfo.STATE = DownloadState.values()[rs.getInt("STATE")];
                mediaInfo.URL = rs.getString("URL");
                mediaInfo.QUALITY = rs.getInt("QUALITY");
                mediaInfo.PRIORITY = rs.getInt("PRIORITY");
                mediaInfo.RELEASED = LocalDate.parse(rs.getString("RELEASED"), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                mediaInfo.ADDED = LocalDate.parse(rs.getString("ADDED"), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                mediaInfo.PATH = rs.getString("PATH");
                if (mediaInfo.TYPE.equals(MediaType.EPISODE)) {
                    mediaInfo.PARENTSHOWID = new BigInteger(rs.getString("PARENTID"));
                    mediaInfo.PARENTSHOWNAME = rs.getString("PARENTNAME");
                    mediaInfo.EPISODE = rs.getFloat("EPISODE");
                }

                extractedMediaInfo.add(mediaInfo);
            }

            stmt.close();
            conn.close();

        }
        catch(Exception e) {
            log.error(e);
        }

        return extractedMediaInfo;
    }


        private class DatabaseEntryAttribute{
        String NAME;
        String INITPARAM;
        Class DATATYPE;

        public DatabaseEntryAttribute(String mName, String mInitialisationParam, Class mDataType){
            this.NAME = mName;
            this.INITPARAM = mInitialisationParam;
            this.DATATYPE = mDataType;
        }
    }
}
