package com.nlscan.uhf.bu;

import android.content.Context;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public class FileUtil {




    public static void writeFile(String json, String FilePath) {

        try {
            File file = new File(FilePath);

            // if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            } else {
                file.delete();
                file.createNewFile();
            }

            // true = append file
            FileWriter fileWritter = new FileWriter(file, false);
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            bufferWritter.write(json);
            bufferWritter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }




    //从磁盘路径读取json文件
    public static String readJsonFile(String fileName) {
        String jsonStr = "";
        try {
            File jsonFile = new File(fileName);
            Reader reader = new InputStreamReader(new FileInputStream(jsonFile),"utf-8");
            int ch = 0;
            StringBuffer sb = new StringBuffer();
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
            reader.close();
            jsonStr = sb.toString();
            return jsonStr;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }




    //从资源文件读取json文件
    public static String readJsonFile(Context context, int resourceId) {
        if( null == context || resourceId < 0 ){
            return null;
        }
        String jsonStr = "";
        try {
            InputStream inputStream = context.getResources().openRawResource( resourceId );
            Reader reader = new InputStreamReader(inputStream,"utf-8");
            int ch = 0;
            StringBuffer sb = new StringBuffer();
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
            reader.close();
            jsonStr = sb.toString();
            return jsonStr;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    public static void createDir(String path){
        File dir=new File(path);
        if(!dir.exists())
            dir.mkdir();
    }




}
