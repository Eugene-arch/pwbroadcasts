A simple mod which adds command for schedule event broadcasts.
Default message delay is ~15 minutes but may be changed in config or using /pwt delay \<new delay\>

Usage of /pwtournament (/pwt, /pwtour):
```
  global:
    /pwt delay [new_delay] - get or set delay (in minutes)
    /pwt default_prefix [new_default_prefix] - get or set default prefix for new tournaments
    /pwt list - get list of created tournaments
    
  tournaments:
    /pwt create <name> - create a new tournament
    /pwt delete <name> - delete selected tournament
    /pwt on <name|--all> - enable tournament message
    /pwt off <name|--all> - disable tournament message
    /pwt edit <tournament> <args> - edit selected tournament
      
      Valid args for pwt edit:
        prefix <get|set <prefix>|reset> - get or change prefix of message, default is "§4[§bTOURNAMENT§4]
        message <get|set <message>> - get or set tournament broadcast message
```

Permissions:
```
command.minecraft.pwtournament - grants access to manage tournaments
```

Config example (/configs/broadcasts.json):
```
{
    "delay_minutes": 15,
    "default_prefix": "§4[§bTOURNAMENT§4]",
    "messages": {
        "contest": {
            "prefix": "§9[Contest!]",
            "message": "§3Take a part in shop warps contest!",
            "enabled": true
        },
        "monthly": {
            "prefix": "§4[§6Monthly tournaments§4]",
            "message": "§dVisit our forum to take a part in §6Tournament of a month!",
            "enabled": true
        },
        "disabled": {
            "prefix": "§4[Disabled message§4]",
            "message": "You will never seen this message until run /pwt on disabled",
            "enabled": false
        }
    }
}
```
