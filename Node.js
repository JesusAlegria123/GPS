// handler.js
import { DynamoDBClient, PutItemCommand } from "@aws-sdk/client-dynamodb";

const TABLE_NAME = process.env.TABLE_NAME || "DeviceLocations";

const db = new DynamoDBClient({});

export const handler = async (event) => {
  try {
    // Si API Gateway HTTP API v2, el body viene en event.body
    const body = event.body ? JSON.parse(event.body) : null;
    if (!body || !body.deviceId || !body.latitude || !body.longitude) {
      return {
        statusCode: 400,
        body: JSON.stringify({ message: "Payload inválido" })
      };
    }

    const item = {
      deviceId: { S: body.deviceId },
      timestamp: { N: String(body.timestamp || Date.now()) },
      latitude: { N: String(body.latitude) },
      longitude: { N: String(body.longitude) },
    };
    if (body.accuracy !== undefined) item.accuracy = { N: String(body.accuracy) };

    const cmd = new PutItemCommand({
      TableName: TABLE_NAME,
      Item: item
    });

    await db.send(cmd);

    return {
      statusCode: 200,
      body: JSON.stringify({ message: "Guardado" })
    };

  } catch (err) {
    console.error("ERROR LAMBDA:", err);
    return {
      statusCode: 500,
      body: JSON.stringify({ message: "Error interno", error: err.message })
    };
  }
};
