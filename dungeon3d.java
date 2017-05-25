package com.techscreen;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Random;

/**
 * Dungeon3D class generates and holds a dungeon of N rooms as defined by the spec
 **/
public class Dungeon3D {
    // these values are used to tweak the type of dungeon's generated
    private static final double PATH_PORTION = 0.6; // max path length relative to the total rooms
    private static final double RANDOM_MODIFIER = 0.1; // how likely a room selection should be near the start, this grows to 1 as we approach end

    // this is our random number generator
    private Random _rand;

    /**
     * _rooms stores a reference to every dungeon room, we use this to iterate over all room as it is more 
     * efficient then running paths through the dungeon
     *
     * _rooms is O(N) storage complexity, with each room occupying 33 bytes of data
     */
    private ArrayList<Room> _rooms = new ArrayList<Room>();


    /**
     * The defualt constructor sets a fully random dungeon generator
     */
    public Dungeon3D(){
        _rand = new Random();
    }

    /**
     * This allows us to seed the dungeon, in case we want to provide re-playable dungeons?
     *
     * @param seed      random number generator seed value
     */
    public Dungeon3D(long seed){
        _rand = new Random(seed);
    }

    /**
     * generate a new dungeon of size 'rooms'
     * O(N^2) runtime (due to looped call to randomValidDirection() and connectAllAdjacent() which are both O(N))
     *
     * @param rooms     the number of rooms to put in the dungeon
     * @return          ArrayList of all rooms, or null on failure
     */
    public ArrayList<Room> generate(int rooms){
        // as per definition dungeon must have (2) or more rooms
        if(rooms < 2){
            return null;
        }

        // path length is randomly selected below size PATH_PORTION*rooms
        int path = _rand.nextInt((int)PATH_PORTION*rooms)+1; // random starts at 0
        if(path < 2){ path = 2; } // path must be at least 2 length

        // all remaining rooms generated from spare
        int spare = rooms-path;

        // first we generate a full path from start to end, thus ensuring there IS a path from start to end
        Room current_room = new Room();
        while(path-- > 0){
            if(current_room == null){
                // initialize with a start room
                current_room = new Room();
            }
            else{
                // randomValidDirection() is O(1) runtime
                int direction = randomValidDirection(current_room);

                // It is very unlikely that we would ever get stuck at this point with no more valid directions.
                //
                // The only condition this could ever happen is what I call the 'snake' condition, when you simultaneously 
                // win and lose at the same time, where snake is so long he fills the screen and bumps into his own tail
                // (I have seen this happen back on the old nokia phones, it is indeed possible).
                //
                // Just to be on the safe side though, we check for this condition and if in the very unlikely event we hit a
                // dead-end, where we are surrounded by rooms on all sides, lets add the remaining 'path' portion to spare and
                // break out of this loop
                if(direction == -1){
                    spare += path+1; // we add +1 because path was already decremented but no room yet added
                    break;
                }

                // we have a valid direction so lets add the room
                current_room = new Room(current_room, direction);

                // connect this room to any existing adjacent rooms
                // connectAllAdjacent() is O(N) runtime
                connectAllAdjacent(current_room);
            }

            // check if we are at the end room, if so mark it
            if(path == 0){
                current_room.end(true);
            }

            // add the current room to ArrayList
            _rooms.add(current_room);
        }

        // next we add random rooms along the existing path until we run out of spare rooms
        // iterate over all existing rooms in order start->end and add random paths away from the main path
        int pathSize = _rooms.size();
        for(int i = 0; i < pathSize; i++){
            if(spare == 0){
                // we are done now
                break;
            }

            // set the current room to the current iteration of the main path
            current_room = _rooms.get(i);
            
            // generate a maxLength for the current random path
            int subPath;
            if(i == pathSize-1){
                // this ensures we have no spares at the end
                subPath = spare;
            }
            else{
                // update the modifer based on the remaining pathSize
                // pathSize-1 will always be > 1 since our loop checks i<pathSize
                //
                // eg, RANDOM_MODIFIER = 0.1, pathSize = 10
                //      starting condition:
                //          (0.1/10)/0.1  = 0.1
                //      end conditions:
                //          (0.1/1)/0.1 = 1
                float modifier = (float)(RANDOM_MODIFIER/(pathSize-i))/(float)RANDOM_MODIFIER;

                // pick a random path length
                // we use a random_modifier here that starts low (defined static final) but grows to 1 as we approach the end room
                //
                // growth to 1 ensures that if we have lots of spare left over we are more likely to have long paths towards the end
                // starting value allows us to tweak how the random generation is built up, 
                //      a low starting value means we get more rooms towards the end
                //      a high starting value means we get more rooms towards the start of the dungeon
                //      a medium starting value should give us an even(ish) distributioni
                //
                // Since we don't want short paths, we /2 to get a half-size random-int then multiply the result by 2 to ensure
                // subPath is ~double what it would be otherwise
                //
                // Additionally, we ensure modifier*spare > 2 and the final subPath <= spare in all cases
                int random = (int)(modifier*spare/2)>1?(int)(modifier*spare/2):2;
                subPath = _rand.nextInt(random)*2;
                if(subPath > spare){ subPath = spare; }
            }

            // this runs in O(subPath*N) the SUM of subPaths over all iterations is < N, so it is safe to assume total runtime of the 
            // containing for{} loop is the same as the runtime of this contained while loop, where SUM subPaths < N
            // O(SUM(subPaths)*N) = O(spare*N) = O(N^2)
            while(subPath-- > 0){
                // randomValidDirection() is O(1) runtime
                int direction = randomValidDirection(current_room);
                
                // if we did not find a valid direction break out of this sub-path and continue onto the next room in the main path
                if(direction == -1){
                    break;
                }

                // add the room as it is not a direct conflict with any existing room
                current_room = new Room(current_room, direction);

                // connect this room to any adjacent rooms
                //
                // connectAllAdjacent() is O(N) runtime
                connectAllAdjacent(current_room);

                // add the current room to ArrayList
                _rooms.add(current_room);

                // we added a room so we can decrement the spare counter
                spare--;
            }
        }

        // if we got here we're done generating, return true to indicate everything went well
        return _rooms;
    }

    /**
     * check if x/y/z coordinate overlaps any room in the current _rooms ArrayList
     * O(N) runtime
     *
     * @param x     the X coordinate
     * @param y     the Y coordinate
     * @param z     the Z coordinate
     *
     * @return      Room if found, or NULL if not found
     */
    private Room findRoom(int x, int y, int z){
        for(Room room : _rooms){
            // check this room location
            if(room.x() == x && room.y() == y && room.z() == z){
                return room;
            }
        }
        return null;
    }

    /**
     * Connect this room to all existing adjacent rooms if any are present
     * O(N) runtime
     *
     * @param room  the room we are connecting all adjacent rooms to
     */
    private void connectAllAdjacent(Room room){
        // check for adjacent rooms, an adjacent room would have exactly 1 unit offset from the current position on a single axis
        // we need to handle each case here because they are each in a different direction
        //
        // do we always connect these, or just sometimes, or never?  I assume we always connect these from this portion of the spec:
        //    * Given any arbitrary rooms A and B, B is considered adjacent to A, and vice versa, if they have at 
        //      least one path of cardinality two (2) from A to B.
        for(Room current : _rooms){
            if(current.x() == room.x()+1 && current.y() == room.y() && current.z() == room.z()){
                room.connect(current, 1);
            }
            else if(current.x() == room.x()-1 && current.y() == room.y() && current.z() == room.z()){
                room.connect(current, 4);
            }
            else if(current.x() == room.x() && current.y()+1 == room.y() && current.z() == room.z()){
                room.connect(current, 2);
            }
            else if(current.x() == room.x() && current.y()-1 == room.y() && current.z() == room.z()){
                room.connect(current, 3);
            }
            else if(current.x() == room.x() && current.y() == room.y() && current.z() == room.z()+1){
                room.connect(current, 0);
            }
            else if(current.x() == room.x() && current.y() == room.y() && current.z() == room.z()-1){
                room.connect(current, 5);
            }
        }
    }

    /**
     * Find a random and valid (un-used and non-overlapped) direction from room
     * O(N) runtime (due to call to findRoom() which is O(N)
     *
     * @param room      the room we want to find a random direction from
     * @return          the direction offset 0-5 as defined by Room._connections decleration or -1 if none valid exist
     */
    private int randomValidDirection(Room room){
        // start in a completely random direction
        int direction = (int)(_rand.nextInt(6));

        // these will represent new coordinates
        int x,y,z;

        // this loops through all 6 directions starting with the randomly selected one, this loop only continues
        // until a non-overlapping room direction is selected
        //
        // because this loop is static (max 6 iterations on any N rooms) we can safely exclude it from our runtime complexity
        // as it is O(6) == O(1) runtime
        for(int i=0; i<6; i++){
            x = room.x();
            y = room.y();
            z = room.z();
            switch(direction){
                case 0: // +1 on the Z axis
                    z = z+1;
                    break;
                case 1: // +1 on the X axis
                    x = x+1;
                    break;
                case 2: // +1 on the Y axis
                    y = y+1;
                    break;
                case 3: // -1 on the Y axis
                    y = y-1;
                    break;
                case 4: // -1 on the X axis
                    x = x-1;
                    break;
                case 5: // -1 on the Z axis
                    z = z-1;
                    break;
                default:
                    return -1;
            }

            // we know the direction is un-used, but we need to check that this room does not overlap another existing room
            // if an overlap is NOT detected break as this direction is safe to build in
            //
            // we don't need to connect to this room here, because connectAllAdjacent() function will do that for us anyways
            //
            // findRoom() is O(N) runtime
            Room found = findRoom(x,y,z);
            if(found == null){
                return direction;
            }

            // modulus safely avoids overflow as we loop through all 6 possible directions starting with the randomly selected one
            direction = (direction+1)%5;
        }

        return -1; // no valid direction found
    }
}




/**
 * This class holds a single dungeon room and associated room-connections info
 *
 * Total storage: 33 bytes total
 *      _x: 4 bytes
 *      _y: 4 bytes
 *      _z: 4 bytes
 *      _end: 1 byte (VM-dependant, assumed 1 byte)
 *      _connections: 6*4 bytes, 24 bytes
 */
public class Room {
    /**
     * we store the coordinate offset from start room, start_room being x=0,y=0,z=0
     * these are used to ensure rooms do not overlap
     */
    private int _x;
    private int _y;
    private int _z;

    private boolean _end;

    /**
     * a room can have up to 6 connections (1 per side), store these in an Array
     * where present/true, connections are always connected on their inverse sides, 
     * eg where room A and B are connected on any single edge:
     *      A[0] z+ connects to B[5] z- 
     *      A[1] x+ connects to B[4] x-
     *      A[2] y+ connects to B[3] Y-
     *      A[3] y- connects to B[2] y+
     *      A[4] x- connects to B[1] x+
     *      A[5] z- connects to B[0] z+
     */
    private Room[] _connections = new Room[6];
    
    /**
     * accessors
     */
    public int x() { return _x; }
    public void x(int x) { _x = x; }

    public int y() { return _y; }
    public void y(int y) { _y = y; }

    public int z() { return _z; }
    public void z(int z) { _z = z; }

    public Room connection(int index) { return _connections[index]; }
    public void connection(int index, Room room) { _connections[index] = room; }

    public boolean end(){ return _end; }
    public void end(boolean end){ _end = end; }
    public boolean start(){ return (_x==0 && _y==0 && _z==0)?true:false; }

    /**
     * default constructor creates a room with location 0,0,0
     */
    public Room(){
        _x = 0;
        _y = 0;
        _z = 0;
    }

    /** 
     * create a room connected from 'parent' in desired direction
     * location is validated during creation
     *
     * @param parent        the parent room this one came connected to
     * @param direction     the direction this room connected from its parent
     */ 
    public Room(Room parent, int direction){
        int x = parent.x();
        int y = parent.y();
        int z = parent.z();
        switch(direction){
            case 0: // +1 on the Z axis
                z = z+1;
                break;
            case 1: // +1 on the X axis
                x = x+1;
                break;
            case 2: // +1 on the Y axis
                y = y+1;
                break;
            case 3: // -1 on the Y axis
                y = y-1;
                break;
            case 4: // -1 on the X axis
                x = x-1;
                break;
            case 5: // -1 on the Z axis
                z = z-1;
                break;
            default:
                // we should throw an exception here, but for the purpose of this assignment we can assume direction <= 5
                // as it is always random selected [0-5]
        }

        // room creation collisions require a working map of existing rooms these are 
        // handled during room generation rather then here to minimize local scope

        // set the coordinates to the incremented values above
        _x = x;
        _y = y;
        _z = z;

        // set the connection between this room and its parent
        // connection is made on both ends, but is viewed from 'this' room to the parent room
        // so we want to use the inverse direction as explained in _connections declaration
        connect(parent, 5-direction);
    }

    /**
     * create a connection from the current room to 'neighbor'
     *
     * @param neighbor     the room we are connecting to
     * @param direction    the direction of the connection, from 'this' view
     */
    public void connect(Room neighbor, int direction){
        connection(direction, neighbor);
        neighbor.connection(5-direction, this);
    }
}
