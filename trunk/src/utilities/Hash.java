
package utilities;


public class Hash
{
    public static void main(String [] args)
    {        
        //java.util.Formatter f = new java.util.Formattr();
        //example string that needs leading zeroes added : "smb://ONYX/Data/compressed/TV Shows/Lost/Season.5/Lost.S05E04.The.Little.Prince.avi"        
        System.out.println(Hash.generateCRC("stack://smb://localhost/c$/dropbox/Movies/The Hangover (2009).part1.wmv , smb://localhost/c$/dropbox/Movies/The Hangover (2009).part2.wmv"));
        //06a0692e               
    }              
    
    public static String generateCRC(String source)
    {
       //convert to lowercase source for any chars under 128       
       char[] chars = source.toCharArray();
       int sourceLength = source.length();
       for (int index = 0; index < sourceLength; index++)
       {
           char c = chars[index];           
           if (c < 128)//convert to lowercase if char is < 128 (ascii)
           {
              //overwrite with lowercase char
              chars[index] = Character.toLowerCase(c);//convert the char to lowercase
           }           
       }       

       //convert to crc, using the lowercase string
       String lowerCaseSource = new String(chars);
       int crc = 0xffffffff;//init
       try
       {
           byte[] bytes = lowerCaseSource.getBytes("UTF-8");
           for(byte b : bytes)
           {
               crc ^= ((long)(b) << 24);//use a long to act as an unsigned int
               for (int i = 0; i < 8; i++)
               {
                   if (((crc) & 0x80000000) == 0x80000000)
                   {
                       crc = (crc << 1) ^ 0x04C11DB7;
                   }
                   else
                   {
                       crc <<= 1;
                   }
               }
           }
       }
       catch(Exception x)
       {
           Config.log(Config.ERROR, "Cannot generate CRC hash from string \""+source+"\". Error: "+x.getMessage(),x);
           return null;
       }
       
       //return the hex string representation of the integer
       String crcString = Integer.toHexString(crc);       
       while(crcString.length() < 8)//may need to prepend zeroes because Integer.toHexString doesn't do it
           crcString = "0"+crcString;
       
       //Config.log(Config.DEBUG, "CRC generated from \""+source+"\" = " +crcString + " (int val = "+crc+")");

       return crcString;
    }
}