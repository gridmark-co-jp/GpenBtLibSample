package mydomain.jp.co.gpenbtlibsample;

public class Common {
    // String型変数がnullまたは0文字であるかどうか
    public boolean isEmptyString(String string){
        if(string == null || string.length() <= 0)
            return true;
        else
            return false;
    }

    // byteデータの左からnビット目が 0 か 1 かを取得する
    public boolean ByteIndex(byte b, int n){
        int mask = 1 << n;
        if((b & mask) == 0)
            return false;   // 0
        else
            return true;    // 1
    }
}
