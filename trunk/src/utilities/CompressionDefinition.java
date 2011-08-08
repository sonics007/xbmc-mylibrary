/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package utilities;

import java.util.List;

/**
 *
 * @author bvidovic
 */
public class CompressionDefinition {

    String command, encode_to_ext, name;
    List<String> verificationLines;
    public CompressionDefinition(String name, String command, String encodeToExt, List<String> verificationLines)
    {
        this.name = name;
        this.command = command;
        if(tools.valid(encodeToExt))//normalize the ext. Should not start with dot
        {
            encodeToExt = encodeToExt.trim();
            while(encodeToExt.startsWith("."))
                encodeToExt = encodeToExt.substring(1, encodeToExt.length());
        }
        this.encode_to_ext = encodeToExt;
        this.verificationLines=verificationLines;
       
    }
    public List<String> getVerificationLines()
    {
        return verificationLines;
    }
    public String getCommand()
    {
        return command;
    }
    public String getEncodeToExt()
    {
        return encode_to_ext;
    }
    
    public String getName()
    {
        return name;
    }

    //case-insensitive matching based on name
    public boolean equals(Object o)
    {
        if(!(o instanceof CompressionDefinition)) return false;
        return getName().equalsIgnoreCase( ((CompressionDefinition)o).getName());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + (this.name != null ? this.name.toLowerCase().hashCode() : 0);//always LC. Case-insensitive equals
        return hash;
    }

}
