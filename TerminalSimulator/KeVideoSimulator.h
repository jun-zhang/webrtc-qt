#ifndef KEVIDEOSIMULATOR_H
#define KEVIDEOSIMULATOR_H

#include "libjingle_app/ketunnelcamera.h"
#include "talk/base/messagehandler.h"
#include "libjingle_app/recorderavi.h"


#include "zmq.h"
#include "zmq.hpp"
#include "zhelpers.hpp"
#include "zmsg.hpp"

namespace talk_base {
    class Thread;
    class Buffer;
}

class KeVideoSimulator:public kaerp2p::KeTunnelCamera
{
public:
    enum {
        MSG_SENDFILEVIDEO
    };
    KeVideoSimulator(const std::string &fileName);
    virtual ~KeVideoSimulator();
    virtual bool Init(kaerp2p::PeerTerminalInterface *t);
    virtual void GetCameraVideoInfo(int level, kaerp2p::VideoInfo *info);
    void OnFileReadEnd(kaerp2p::RecordReaderInterface *);
    void OnFileVideoData(const char * data,int len);
    void OnFileAudioData(const char * data,int len);

protected:
    void OnCommandJsonMsg(const std::string &peerId, Json::Value &jmessage);

protected:
    kaerp2p::RecordReaderInterface * reader;
    std::string fileName;

    zmq::context_t *context;
    zmq::socket_t *publisher;
};

#endif // KEVIDEOSIMULATOR_H