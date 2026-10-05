package architecturefixtures.bad.domain;

import java.io.File;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.channels.SocketChannel;
import kotlin.io.FileWalkDirection;
import java.sql.Connection;
import javax.sql.DataSource;

class IoDomain {
    File file;
    Socket socket;
    Path path;
    SocketChannel channel;
    FileWalkDirection walkDirection;
    Connection connection;
    DataSource dataSource;
}
