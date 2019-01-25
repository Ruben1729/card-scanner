package scanner.cards.app.cardscanner;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

public class Sets {

    private volatile HashMap<String, SetInfo> sets = new HashMap();

    public Sets update(){
        new Thread(new Runnable(){
            public void run(){
                try {
                    Document document = Jsoup.connect("https://mtg.gamepedia.com/Set").get();

                    sets.clear();

                    Element tableBody = document.selectFirst("table.sortable").selectFirst("tbody");

                    for(Element row : tableBody.select("tr")){

                        if(row.select("td").isEmpty())
                            continue;

                        String name = row.select("td").get(1).text().trim();
                        String code = row.select("td").get(3).text().trim();
                        code = code.length() >= 3 ? code.substring(0, 3) : code;
                        String type = row.select("td").get(4).text().trim();

                        Date released = null;
                        try {
                            DateFormat df = new SimpleDateFormat("YYYY-MM");

                            released = df.parse(row.select("td").get(0).text().trim());
                        }catch(ParseException e){
                            e.printStackTrace();
                        }

                        SetInfo info = new SetInfo(name, code, type, released);
                        System.out.println(info.toString());
                        sets.put(code, info);

                    }


                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        }).start();

        return this;
    }

    public boolean exists(String code){
        for(String key : sets.keySet())
            if(key.equals(code))
                return true;
        return false;
    }

    public SetInfo get(String code){
        return sets.get(code);
    }

    public String toString(){
        String result = "";
        for(String key : sets.keySet())
            result += sets.get(key).toString() + "\n";
        return result;
    }

}
