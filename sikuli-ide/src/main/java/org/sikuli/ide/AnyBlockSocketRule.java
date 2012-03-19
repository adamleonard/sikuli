/*
 * Copyright 2010-2011, Sikuli.org
 * Released under the MIT License.
 *
 */
package org.sikuli.ide;

import edu.mit.blocks.codeblocks.*;

/**
 * <code>SocketRule</code> checks if the two sockets being matched, where at least one is of type "any", can connect simply by checking if the socket/plug
 * is empty
 *
 */
public class AnyBlockSocketRule implements LinkRule {

    /**
     * Returns true if one socket of one block is of kind "any" and has space for a block; false if not.
     * Both sockets must be empty to return true.
     * @param block1 the associated <code>Block</code> of socket1
     * @param block2 the associated <code>Block</code> of socket2
     * @param socket1 a <code>Socket</code> or plug of block1
     * @param socket2 a <code>Socket</code> or plug of block2
     * @return true if the two sockets of the two blocks can link; false if not
     */
    public boolean canLink(Block block1, Block block2, BlockConnector socket1, BlockConnector socket2) {
        // Make sure that none of the sockets are connected,
        // and that exactly one of the sockets is a plug.
        if (socket1.hasBlock() || socket2.hasBlock()
                || !((block1.hasPlug() && block1.getPlug() == socket1)
                ^ (block2.hasPlug() && block2.getPlug() == socket2))) {
            return false;
        }

        // If one block has kind "any" then they can connect
        if (socket1.getKind().equals("any") ||  socket2.getKind().equals("any")) {
            return true;
        }

        return false;
    }

    public boolean isMandatory() {
        return false;
    }
}
