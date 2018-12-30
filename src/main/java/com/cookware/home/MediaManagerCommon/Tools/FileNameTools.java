package com.cookware.home.MediaManagerCommon.Tools;

import com.bitlove.fnv.FNV;
import com.cookware.home.MediaManagerCommon.DataTypes.MediaInfo;
import com.cookware.home.MediaManagerCommon.DataTypes.MediaType;
import org.apache.log4j.Logger;

import java.math.BigInteger;

/**
 * Created by Kody on 19/09/2017.
 */
public class FileNameTools {
    private static final Logger log = Logger.getLogger(FileNameTools.class);
    private final FNV stringHasher;

    public FileNameTools() {
        stringHasher = new FNV();
    }

    public BigInteger generateHashFromMediaInfo(MediaInfo info){
        String fullFileName = getFullFileNameFromMediaInfo(info);
        return generateHashFromFullFileName(fullFileName);
    }


    public BigInteger generateHashFromFullFileName(String fileName){
        int fileTypeIndex = fileName.lastIndexOf('.');

        if (fileName.contains(".mp4")){
            fileName = fileName.substring(0,fileTypeIndex); // START HERE
        }
        if (fileName.contains(".avi")){
            fileName = fileName.substring(0,fileTypeIndex); // START HERE
        }
        return generateHashFromGeneralMediaName(fileName);
    }


    public BigInteger generateHashFromGeneralMediaName(String name){
        String shortMediaName = name.replaceAll("\\s", "");
        return generateHashFromShortMediaName(shortMediaName);
    }


    public BigInteger generateHashFromShortMediaName(String shortMediaName){
        String shortMediaNameWithoutWhiteSpace = removeSpecialCharactersFromFileName(shortMediaName);
        return this.stringHasher.fnv1a_32(shortMediaNameWithoutWhiteSpace.getBytes());
    }

    public String getFullFileNameFromMediaInfo(MediaInfo mediaInfo){
        String mediaFileName = "";
        if(mediaInfo.TYPE.equals(MediaType.EPISODE)){
            mediaFileName = String.format("%s - S%02dE%02d - %s",
                    mediaInfo.PARENTSHOWNAME,
                    mediaInfo.getSeason(),
                    mediaInfo.getEpisode(),
                    mediaInfo.NAME);
        }else {
            mediaFileName = String.format("%s (%d)",
                    mediaInfo.NAME,
                    mediaInfo.RELEASED.getYear());
        }
        return removeSpecialCharactersFromFileName(mediaFileName);
    }

    public String removeSpecialCharactersFromFileName(String fileName){
        return fileName.replaceAll("[\\/:*?\"<>|]","");
    }

    public String removeSpecialCharactersFromPath(String pathName){
        return pathName.replaceAll("[:*?\"<>|]","");
    }
}
