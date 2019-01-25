package scanner.cards.app.cardscanner;

import java.util.Date;

public class SetInfo {

    private String name;
    private String code;
    private String type;

    private Date released;

    public SetInfo(String name, String code, String type, Date released){
        this.name = name;
        this.code = code;
        this.type = type;
        this.released = released;
    }

    public String getName(){
        return name;
    }

    public String getSetCode(){
        return code;
    }

    public String getType(){
        return type;
    }

    public Date getDateReleased(){
        return released;
    }

    public String toString(){
        return name + "\t" + code + "\t" + type;
    }

}
