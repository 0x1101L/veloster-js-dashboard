package com.veloster.dashboard.debugger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

/** Reads only the Broadcom vendor HAL tag; it never toggles Bluetooth or sends HCI commands. */
public final class BluetoothRuntimeProbe {
    public static final class Snapshot {
        public final String status, controller, patch, time;
        private Snapshot(String status,String controller,String patch,String time){
            this.status=status;this.controller=controller;this.patch=patch;this.time=time;
        }
    }
    private static final class Attempt {
        String controller="",patch="",time="";
        boolean completed,aborted;
    }
    private BluetoothRuntimeProbe(){}

    public static Snapshot read(){
        Process process=null;
        try{
            process=new ProcessBuilder("logcat","-d","-v","time","-t","500","-s","bt_vendor:V").redirectErrorStream(true).start();
            BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"));
            StringBuilder lines=new StringBuilder();String line;int count=0;
            while((line=reader.readLine())!=null&&count++<500&&lines.length()<131072)lines.append(line).append('\n');
            reader.close();
            int exit=process.waitFor();
            if(exit!=0)return unknown("logcat 조회 거부 또는 실패");
            String output=lines.toString().toLowerCase(Locale.US);
            if(output.indexOf("permission denied")>=0||output.indexOf("not allowed")>=0||output.indexOf("access denied")>=0)
                return unknown("시스템 logcat 권한 없음");
            return parse(lines.toString());
        }catch(SecurityException e){return unknown("시스템 logcat 권한 없음");}
        catch(Exception e){return unknown("logcat 조회 실패");}
        finally{if(process!=null)process.destroy();}
    }

    public static Snapshot parse(String text){
        if(text==null||text.length()==0)return unknown("Bluetooth HAL 기록 없음");
        Attempt latest=null;
        String[] lines=text.split("\\r?\\n");
        for(String line:lines){
            String lower=line.toLowerCase(Locale.US);
            if(lower.indexOf("read local name for chipid")>=0){latest=new Attempt();latest.time=timestamp(line);}
            if(latest==null)latest=new Attempt();
            int at=lower.indexOf("read local name result is");
            if(at>=0){
                String name=line.substring(at+"read local name result is".length()).trim().toUpperCase(Locale.US);
                if(name.indexOf("BCM20702")>=0)latest.controller="BCM20702";
                else if(name.indexOf("BCM4335")>=0)latest.controller="BCM4335";
                latest.time=timestamp(line);
            }
            if(lower.indexOf("found patchfile:")>=0){
                String value=line.substring(lower.indexOf("found patchfile:")+"found patchfile:".length()).trim();
                latest.patch=basename(value);latest.time=timestamp(line);
            }
            if(lower.indexOf("change patchfile:")>=0){
                String value=line.substring(lower.indexOf("change patchfile:")+"change patchfile:".length()).trim();
                latest.patch=basename(value);latest.time=timestamp(line);
            }
            if(lower.indexOf("vendor lib fwcfg completed")>=0){latest.completed=true;latest.aborted=false;latest.time=timestamp(line);}
            if(lower.indexOf("vendor lib fwcfg aborted")>=0){latest.aborted=true;latest.completed=false;latest.time=timestamp(line);}
        }
        if(latest==null)return unknown("Bluetooth HAL 기록 없음");
        if(latest.controller.length()==0)return new Snapshot("칩 판별 기록 없음","미확인",latest.patch.length()==0?"미확인":latest.patch,latest.time);
        if(latest.aborted)return new Snapshot("펌웨어 초기화 실패",latest.controller,latest.patch.length()==0?"미확인":latest.patch,latest.time);
        if(latest.completed&&latest.patch.length()>0)return new Snapshot("HAL 펌웨어 초기화 완료",latest.controller,latest.patch,latest.time);
        return new Snapshot("초기화 기록 불완전",latest.controller,latest.patch.length()==0?"미확인":latest.patch,latest.time);
    }
    private static Snapshot unknown(String status){return new Snapshot(status,"미확인","미확인","—");}
    private static String basename(String path){
        int end=path.length();while(end>0&&(path.charAt(end-1)==' '||path.charAt(end-1)=='\r'))end--;
        int slash=path.lastIndexOf('/',end-1);String value=path.substring(slash+1,end);
        StringBuilder clean=new StringBuilder();for(int i=0;i<value.length()&&clean.length()<48;i++){
            char c=value.charAt(i);if((c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='.'||c=='_'||c=='-')clean.append(c);
        }
        return clean.length()==0?"미확인":clean.toString();
    }
    private static String timestamp(String line){
        // logcat -v time prefixes records with MM-DD HH:MM:SS.mmm.
        if(line.length()>=18&&line.charAt(2)=='-'&&line.charAt(5)==' '&&line.charAt(8)==':'&&line.charAt(11)==':')return line.substring(0,18).trim();
        return "시각 미확인";
    }
}
