package com.zelkatani.conquest.multiplayer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.zelkatani.conquest.Player;
import com.zelkatani.conquest.entities.Tile;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class Client {
    private Socket socket;
    private DataOutputStream outputStream;
    private BufferedReader reader;

    private Json json;
    private SerialArray<Tile> map;

    private Array<Player> players;
    private Player player;

    public Client(Player player, SerialArray<Tile> map) {
        players = new Array<>();
        this.player = player;
        this.map = map;

        json = new Json(JsonWriter.OutputType.json);
        json.setSerializer(Packet.class, new Json.Serializer<Packet>() {
            @Override
            public void write(Json json, Packet object, Class knownType) {
                json.writeObjectStart();
                object.write(json);
                json.writeObjectEnd();
            }

            @Override
            public Packet read(Json json, JsonValue jsonData, Class type) {
                JsonValue tiles = jsonData.get("tiles");
                JsonValue player = jsonData.get("player");
                if (tiles == null) {
                    Client.this.player.setId(jsonData.getInt("id"));
                    return null;
                } else {
                    if (!containsPlayer(player.getInt("id"))) {
                        Tile cap = map.get(player.get("capital").getInt("index"));
                        String color = player.getString("color");

                        Player opponent = new Player(Color.valueOf(color), cap);
                        opponent.setId(player.getInt("id"));
                        players.add(opponent);
                    }
                }

                for (JsonValue tile : tiles) {
                    Tile t = map.get(tile.getInt("index"));
                    for (Player p : players) {
                        if (p.getId() == tile.getInt("owner")) {
                            t.setOwner(p);
                            break;
                        }
                    }
                    t.read(json, tile);
                }
                return null;
            }
        });

        try {
            socket = new Socket("", 8080);
            outputStream = new DataOutputStream(socket.getOutputStream());
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException io) {
            io.printStackTrace();
        }

        Thread receiveThread = new Thread(this::receive);
        receiveThread.start();
    }

    private boolean containsPlayer(int id) {
        for (Player player : players) {
            if (player.getId() == id) return true;
        }
        return false;
    }

    public void update() {
        send();
    }

    private void send() {
        try {
            Packet packet = new Packet(player, map);
            outputStream.writeBytes(json.toJson(packet));
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    private void receive() {
        while (true) {
            try {
                String str = reader.readLine();
                json.fromJson(Packet.class, str);
            } catch (IOException io) {
                io.printStackTrace();
            }
        }
    }
}
