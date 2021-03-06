/*
 **** BEGIN LICENSE BLOCK *****
 * Version: EPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/

package org.jruby.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

public class JRubyClassLoader extends ClassDefiningJRubyClassLoader {

    private static final Logger LOG = LoggerFactory.getLogger("JRubyClassLoader");

    private Runnable unloader;

    public JRubyClassLoader(ClassLoader parent) {
        super(parent);
    }

    // Change visibility so others can see it
    @Override
    public void addURL(URL url) {
        // if we have such embedded jar within a jar, we copy it to temp file and use the
        // the temp file with the super URLClassLoader
        if ( url.toString().contains( "!/" )) {
            InputStream in = null;
            OutputStream out = null;
            try
            {
                File f = File.createTempFile( "jruby", new File(url.getFile()).getName());
                f.deleteOnExit();
                out = new BufferedOutputStream( new FileOutputStream( f ) );
                in = new BufferedInputStream( url.openStream() );
                int i = in.read();
                while( i != -1 ) {
                    out.write( i );
                    i = in.read();
                }
                out.close();
                in.close();
                url = f.toURI().toURL();
            }
            catch (IOException e)
            {
                throw new RuntimeException("BUG: we can not copy embedded jar to temp directory", e);
            }
            finally {
                // make sure we close everything
                if ( out != null ) {
                    try
                    {
                        out.close();
                    }
                    catch (IOException e)
                    {
                    }
                }
                if ( in != null ) {
                    try
                    {
                        in.close();
                    }
                    catch (IOException e)
                    {
                    }
                }
            }
        }
        super.addURL( url );
    }

    /**
     * Called when the parent runtime is torn down.
     */
    public void tearDown(boolean debug) {
        try {
            // A hack to allow unloading all JDBC Drivers loaded by this classloader.
            // See http://bugs.jruby.org/4226
            getJDBCDriverUnloader().run();
        } catch (Exception e) {
            if (debug) LOG.debug(e);
        }
    }

    public synchronized Runnable getJDBCDriverUnloader() {
        if (unloader == null) {
            try {
                InputStream unloaderStream = getClass().getResourceAsStream("/org/jruby/util/JDBCDriverUnloader.class");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int bytesRead;
                while ((bytesRead = unloaderStream.read(buf)) != -1) {
                    baos.write(buf, 0, bytesRead);
                }

                Class<?> unloaderClass = defineClass("org.jruby.util.JDBCDriverUnloader", baos.toByteArray());
                unloader = (Runnable) unloaderClass.newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return unloader;
    }
}
