import asyncio
import logging
from livekit import agents
from livekit.agents import JobContext, WorkerOptions, cli, Agent, AgentSession
from livekit.plugins import google

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("vanta-agent")

async def entrypoint(ctx: JobContext):
    try:
        logger.info(f"Connecting to room {ctx.room.name}")
        await ctx.connect()

        participant = await ctx.wait_for_participant()
        logger.info(f"Participant connected: {participant.identity}")

        # Use Gemini Realtime Model
        # Note: Requires GOOGLE_API_KEY env var
        model = google.realtime.RealtimeModel(
            model="gemini-2.0-flash-exp",
            instructions="You are Vanta, a helpful assistant.",
        )

        # AgentSession manages the interaction
        session = AgentSession(
            llm=model,
        )
        
        agent = Agent(
            instructions="You are Vanta.", 
        )

        await session.start(ctx.room, agent)
        
        # Greet
        await session.generate_reply(instructions="Say hello to the user and introduce yourself as Vanta.")

        # Wait for completion
        await asyncio.Future()
    except Exception as e:
        logger.error(f"CRITICAL ERROR: {e}", exc_info=True)


if __name__ == "__main__":
    cli.run_app(WorkerOptions(entrypoint_fnc=entrypoint))
