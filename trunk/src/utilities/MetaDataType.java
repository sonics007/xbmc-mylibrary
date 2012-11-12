/*
Copyright (C) 2012 Brady Vidovic

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package utilities;

/**
 * 
 * @author Brady Vidovic
 */
public enum MetaDataType {
    MOVIE_SET, MOVIE_TAGS, PREFIX, SUFFIX;
    
    boolean isForMovieOnly(){
        return this == MOVIE_SET || this == MOVIE_TAGS;
    }
    
    boolean isXFix(){
        return this == PREFIX || this == SUFFIX;
    }
    
}
