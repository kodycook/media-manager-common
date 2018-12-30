package com.cookware.home.MediaManagerCommon.Managers;

import com.cookware.home.MediaManagerCommon.DataTypes.Config;
import com.cookware.home.MediaManagerCommon.DataTypes.DownloadState;
import com.cookware.home.MediaManagerCommon.DataTypes.MediaInfo;
import com.cookware.home.MediaManagerCommon.DataTypes.MediaType;
import com.cookware.home.MediaManagerCommon.Tools.FileNameTools;
import com.cookware.common.Tools.WebTools;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Created by Kody on 13/09/2017.
 */
public class DownloadManager {
    // TODO: Implement interfaces to create different versions of the web scrapers
    private static final Logger log = Logger.getLogger(DownloadManager.class);
    private final Config config;
    private final int downloadBlockSize = 2048;
    private final WebTools webTools = new WebTools();
    private final FileNameTools fileNameTools = new FileNameTools();
    private final DatabaseManager databaseManager;

    public DownloadManager(DatabaseManager mDatabaseManager, Config mConfig){
        config = mConfig;
        this.databaseManager = mDatabaseManager;
    }

    public MediaInfo downloadMedia(MediaInfo mediaInfo){
        log.info(String.format("Starting download: %s", mediaInfo.toString()));
        boolean downloadSuccess;
        //TODO: Add Redirect to media download
        final DownloadLink embeddedMediaUrlAndQuality = bridgeToVideoMe(mediaInfo);
        if(embeddedMediaUrlAndQuality == null){
            log.error(String.format("This media item will need to be downloaded manually and has been set to the \"IGNORED\" state in the Database"));
            mediaInfo.STATE = DownloadState.IGNORED;
            databaseManager.updateState(mediaInfo.ID, mediaInfo.STATE);
            return null;
        }
        if(embeddedMediaUrlAndQuality.url.equals("")){
            mediaInfo.STATE = DownloadState.FAILED;
            databaseManager.updateState(mediaInfo.ID, mediaInfo.STATE);
            return null;
        }
        mediaInfo.QUALITY = embeddedMediaUrlAndQuality.quality;
        mediaInfo.PATH = fileNameTools.getFullFileNameFromMediaInfo(mediaInfo);

        mediaInfo.PATH += embeddedMediaUrlAndQuality.fileType;

        databaseManager.updatePath(mediaInfo.ID, (mediaInfo.PATH));
        databaseManager.updateQuality(mediaInfo.ID, mediaInfo.QUALITY);
        downloadSuccess = newDownload(embeddedMediaUrlAndQuality.url, mediaInfo.PATH, mediaInfo.TYPE);
        if(!downloadSuccess){
            log.error(String.format("Issue downloading: %s",mediaInfo.toString()));
            mediaInfo.STATE = DownloadState.FAILED;
            databaseManager.updateState(mediaInfo.ID, mediaInfo.STATE);
            return null;
        }


        mediaInfo.STATE = DownloadState.TRANSFERRING;
        databaseManager.updateState(mediaInfo.ID, mediaInfo.STATE);
        databaseManager.updateDownloadDate(mediaInfo.ID, LocalDate.now());
        log.info(String.format("Finished downloading: %s",mediaInfo.toString()));
        return mediaInfo;
    }

    protected DownloadLink bridgeToVideoMe(MediaInfo mediaInfo){
        final String html = webTools.getWebPageHtml(mediaInfo.URL);
        if(html.equals("")){
            log.error(String.format("Unknown issue obtaining html for %s", mediaInfo.URL));
            return new DownloadLink("", 0, "");
        }
        final String urlExtension = findVideoMeLinkInHtml(html);
        if (urlExtension == null){
            log.error(String.format("No links found at %s", mediaInfo.URL));
            return null;
        }
        else if (urlExtension.equals("")){
            log.error(String.format("No video.me link found at %s", mediaInfo.URL));
            return null;
        }
        final String videoMeUrl = webTools.extractBaseURl(mediaInfo.URL) + urlExtension;
        String redirectedUrl = webTools.getRedirectedUrl(videoMeUrl);
        if(redirectedUrl == null) {
            log.error(String.format("Timeout for redirect URL", videoMeUrl));
            return new DownloadLink("", 0, "");
        }
        if(redirectedUrl.equals("")) {
            log.error(String.format("Could not obtain redirect URL for %s (%s)", mediaInfo.NAME, videoMeUrl));
            return null;
        }
        List<DownloadLink> mediaDownloadLinks = extractAllMediaUrls(redirectedUrl);
        if(mediaDownloadLinks == null){
            return null;
        }
        else if(mediaDownloadLinks.isEmpty()){
            return new DownloadLink("", 0, "");
        }

        return selectBestLinkByQuality(mediaDownloadLinks, mediaInfo.QUALITY);
    }


    public String findVideoMeLinkInHtml(String html){
        Document document = Jsoup.parse(html);
        Elements matchedLinks = document.getElementsByTag("table");
        if(matchedLinks.isEmpty()){
            log.debug("No entries found, please try again!");

            return null;
        }

        int i = 1;
        String site;
        String url = "";
        for (Element matchedLink : matchedLinks) {
            if(matchedLink.hasAttr("class")) {
                site = "";
                if(matchedLink.getElementsByClass("version_host").tagName("script").html().split("'").length > 1) {
                    try {
                        site = matchedLink.getElementsByClass("version_host").tagName("script").html().split("'")[1];
                    } catch (Exception e) {
                        log.error(e);
                    }
                    if(site.equals("thevideo.me")){
                        url = matchedLink.getElementsByAttribute("href").attr("href");
                        break;
                    }
                    i++;
                }
            }
        }
        return url;
    }


    public List<DownloadLink> extractAllMediaUrls(String url){
        // TODO: Clean up this function
        Scanner scan;
        String logicalLine;
        String firstPage;
        String secondPage;

        firstPage = webTools.getWebPageHtml(url);
        if(firstPage.equals("")){
            return new ArrayList<>();
        }
        Document document = Jsoup.parse(firstPage);
        if(document.getElementsByAttributeValue("name", "hash").size() == 0){
            int startOfLinksInFirstWebPage = firstPage.indexOf("sources: [");
            if (startOfLinksInFirstWebPage == -1){
                log.error(String.format("Error retrieving hash code from %s",url));
                return null;
            }
            else secondPage = firstPage;
            log.info("No \"Proceed to video\" page for this media");
        }
        else {
            String hash = document.getElementsByAttributeValue("name", "hash").get(0).attr("value");

            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("_vhash", "i1102394cE"));
            params.add(new BasicNameValuePair("gfk", "i22abd2449"));
            params.add(new BasicNameValuePair("hash", hash));
            params.add(new BasicNameValuePair("inhu", "foff"));

            secondPage = webTools.getWebPageHtml(url, WebTools.HttpRequestType.POST, params);
        }

        if(secondPage.equals("")){
            return new ArrayList<>();
        }

        String encodedHash = getHashFromMediaPage(secondPage);
        if (encodedHash.equals("")){
            return new ArrayList<>();
        }

        return findMediaOnPage(secondPage, encodedHash);
    }


    private String getHashFromMediaPage(String pageHtml){
        Scanner scan;
        String logicalLine;

        int startOfUrlCodeInWebPage = pageHtml.indexOf("lets_play_a_game='");

        scan = new Scanner(pageHtml.substring(startOfUrlCodeInWebPage+"lets_play_a_game='".length()));
        scan.useDelimiter(Pattern.compile("'"));
        logicalLine = scan.next();

        String thirdPage = webTools.getWebPageHtml("https://thevideo.me/vsign/player/"+logicalLine);
        if(thirdPage.equals("")){
            return "";
        }

        String[] encodedAttributes = thirdPage.split("\\|");

        String encodedHash = "";
        for (String temp:encodedAttributes){
            if(temp.length()==282){
                encodedHash = temp;
                break;
            }
        }
        return encodedHash;
    }


    private List<DownloadLink> findMediaOnPage(String pageHtml, String encodedHash){
        Scanner scan;
        String logicalLine;

        int startOfLinksInWebPage = pageHtml.indexOf("sources: [");
        scan = new Scanner(pageHtml.substring(startOfLinksInWebPage+11));
        scan.useDelimiter(Pattern.compile("}]"));
        logicalLine = scan.next();
        String[] rawMediaSources = logicalLine.split("\\},\\{");
        String fileType;

        List<DownloadLink> mediaLinks = new ArrayList<DownloadLink>();
        for (String source:rawMediaSources){
            String[] rawSeperatedValues = source.split("\"");
            try{
                String downloadUrl = rawSeperatedValues[3];
                fileType = downloadUrl.substring(downloadUrl.length()-4);

                if (!(fileType.charAt(0) == '.')) {
                    continue;
                }
                else {
                    int quality = Integer.parseInt(rawSeperatedValues[7].replaceAll("[^0-9]", ""));
                    mediaLinks.add(new DownloadLink(downloadUrl + "?direct=false&ua=1&vt=" + encodedHash, quality, fileType));
                }

            } catch (Exception e){
                log.error("Seperation of download links failed");
            }
        }
        return mediaLinks;
    }

    private DownloadLink selectBestLinkByQuality(List<DownloadLink> mediaLinks, int quality){
        if (mediaLinks.isEmpty()){
            return null;
        }
        if(quality == -1){
            return selectLinkWithHighestQuality(mediaLinks);
        }
        else if (quality == 0){
            return selectLinkWithLowestQuality(mediaLinks);
        }
        else{
            return selectLinkClosestToSpecifiedQuality(mediaLinks, quality);
        }
    }



    private DownloadLink selectLinkWithHighestQuality(List<DownloadLink> mediaLinks){
        int highestQuality = 0;
        DownloadLink result = null;
        for (DownloadLink mediaLink:mediaLinks){
            if(highestQuality < mediaLink.quality){
                result = mediaLink;
                highestQuality = mediaLink.quality;
            }
        }
        return result;
    }

    private DownloadLink selectLinkWithLowestQuality(List<DownloadLink> mediaLinks){
        int lowestQuality = Integer.MAX_VALUE;
        DownloadLink result = null;
        for (DownloadLink mediaLink:mediaLinks){
            if(lowestQuality > mediaLink.quality){
                result = mediaLink;
                lowestQuality = mediaLink.quality;
            }
        }
        return result;
    }


    private DownloadLink selectLinkClosestToSpecifiedQuality(List<DownloadLink> mediaLinks, int specifiedQuality){
        int finalQualityDifference = Integer.MAX_VALUE;
        int currentQualityDifference;
        DownloadLink result = null;
        for (DownloadLink mediaLink:mediaLinks){
            currentQualityDifference = Math.abs(mediaLink.quality-specifiedQuality);
            if(currentQualityDifference < finalQualityDifference){
                result = mediaLink;
                finalQualityDifference = currentQualityDifference;
            }
        }
        return result;
    }


    public boolean newDownload(String downloadUrl, String downloadFilename, MediaType type){
        String downloadFilepath = "";
        downloadFilepath = config.tempPath;
        File output = new File(downloadFilepath, downloadFilename);
        try {
            return downloadMediaToFile(downloadUrl, output);
        } catch (Throwable throwable) {
            log.error(String.format("Error downloading media: %s (%s)\n", downloadFilename, throwable.getMessage()));
            return false;
        }
    }


    private boolean downloadMediaToFile(String downloadUrl, File outputfile) throws Throwable {
        long startTime = System.currentTimeMillis();
        URL url = new URL(downloadUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(30000);

        if (conn.getResponseCode() == 200) {
            InputStream inputStream = new BufferedInputStream(conn.getInputStream());
            long length = Long.parseLong(conn.getHeaderFields().get("Content-Length").get(0));
            System.out.println("Writing " + length + " bytes to " + outputfile);
            if (outputfile.exists()) {
                outputfile.delete();
            }
            FileOutputStream outstream = new FileOutputStream(outputfile);
            int i = 1;
            try {
                byte[] buffer = new byte[this.downloadBlockSize];
                int count = -1;
                try {
                    while ((count = inputStream.read(buffer)) != -1) {
                        printProgress(startTime, (int) length / this.downloadBlockSize + 1, i);
                        i++;
                        outstream.write(buffer, 0, count);
                    }
                }
                catch (SocketException e){
                    log.error(String.format("Issue downloading: %s", outputfile));
                    return false;
                }
                catch (SocketTimeoutException e){
                    log.error("Download timed out mid-download");
                    return false;
                }

                outstream.flush();
            } finally {
                outstream.close();
            }
            System.out.print("\n\n");
        }
        return true;
    }


    private void printProgress(long startTime, long total, long current) {
        // TODO: Try find a way to stop reprinting of the status bar as new logs are sent to the console
        long eta = current == 0 ? 0 :
                (total - current) * (System.currentTimeMillis() - startTime) / (current);


        String etaHms = current == 0 ? "N/A" :
                String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(eta),
                        TimeUnit.MILLISECONDS.toMinutes(eta) % TimeUnit.HOURS.toMinutes(1),
                        TimeUnit.MILLISECONDS.toSeconds(eta) % TimeUnit.MINUTES.toSeconds(1));

        StringBuilder string = new StringBuilder(140);
        int percent = (int) (current * 100 / total);
        string
                .append('\r')
                .append(String.join("", Collections.nCopies(percent == 0 ? 2 : 2 - (int) (Math.log10(percent)), " ")))
                .append(String.format(" %d%% [", percent))
                .append(String.join("", Collections.nCopies(percent, "=")))
                .append('>')
                .append(String.join("", Collections.nCopies(100 - percent, " ")))
                .append(']')
                .append(String.join("", Collections.nCopies((int) (Math.log10(total)) - (int) (Math.log10(current)), " ")))
                .append(String.format(" %.2f/%.2fMB ", ((double) current)*this.downloadBlockSize/Math.pow(2,20),
                        ((double) total)*this.downloadBlockSize/Math.pow(2,20)))
                .append(String.format("(%.2fMB/s), ", ((double) current)*this.downloadBlockSize/Math.pow(2,20)/
                        TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTime)))
                .append(String.format("ETA: %s", etaHms));

        System.out.print(string);
    }


    public class DownloadLink{
        public String url;
        public int quality;
        public String fileType;

        private DownloadLink(String mUrl, int mQuality, String mFileType){
            this.url = mUrl;
            this.quality = mQuality;
            this.fileType = mFileType;
        }

        public String toString(){
            return String.format("(%dp) [%s] %s",this.quality, this.fileType.substring(1), this.url);
        }
    }
}
