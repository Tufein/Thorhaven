// Test-only Linux uinput fixture, not packaged in the APK.
#include <linux/input.h>
#include <linux/uinput.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstdio>
#include <cstring>
#include <string>
int main(){int u=open("/dev/uinput",O_WRONLY|O_NONBLOCK);if(u<0)return 2;ioctl(u,UI_SET_EVBIT,EV_KEY);ioctl(u,UI_SET_EVBIT,EV_ABS);for(int k:{304,305,314,315})ioctl(u,UI_SET_KEYBIT,k);for(int a:{ABS_X,ABS_Y,ABS_RX,ABS_RY,ABS_HAT0X,ABS_HAT0Y}){ioctl(u,UI_SET_ABSBIT,a);uinput_abs_setup s{};s.code=a;s.absinfo.minimum=a>=ABS_HAT0X?-1:-32768;s.absinfo.maximum=a>=ABS_HAT0X?1:32767;ioctl(u,UI_ABS_SETUP,&s);}uinput_setup s{};strcpy(s.name,"Thorhaven Test Source");s.id.bustype=BUS_USB;s.id.vendor=0x045e;s.id.product=0x028e;ioctl(u,UI_DEV_SETUP,&s);if(ioctl(u,UI_DEV_CREATE))return 3;
 const char* commands="/data/local/tmp/thorhaven-fixture.commands";unlink(commands);mkfifo(commands,0666);chmod(commands,0666);int fifo=open(commands,O_RDWR|O_NONBLOCK);FILE*log=fopen("/data/local/tmp/thorhaven-fixture.log","w");chmod("/data/local/tmp/thorhaven-fixture.log",0644);int observer=-1,physical=-1;std::string buffer;
 for(int tick=0;tick<120000;tick++){if(observer<0)for(int i=0;i<256;i++){std::string p="/dev/input/event"+std::to_string(i);int fd=open(p.c_str(),O_RDONLY|O_NONBLOCK);char name[256]={};if(fd<0)continue;ioctl(fd,EVIOCGNAME(sizeof(name)),name);if(!strcmp(name,"Thorhaven Controller")){observer=fd;break;}close(fd);}
 if(physical<0)for(int i=0;i<256;i++){std::string p="/dev/input/event"+std::to_string(i);int fd=open(p.c_str(),O_RDONLY|O_NONBLOCK);char name[256]={};if(fd<0)continue;ioctl(fd,EVIOCGNAME(sizeof(name)),name);if(!strcmp(name,"Thorhaven Test Source")){physical=fd;break;}close(fd);}
 if(physical>=0){input_event e;while(read(physical,&e,sizeof(e))==sizeof(e)){fprintf(log,"SRC %d %d %d\n",e.type,e.code,e.value);fflush(log);}}
 char bytes[1024];int n=read(fifo,bytes,sizeof(bytes));if(n>0)buffer.append(bytes,n);size_t pos;while((pos=buffer.find('\n'))!=std::string::npos){std::string line=buffer.substr(0,pos);buffer.erase(0,pos+1);int type,code,value;if(sscanf(line.c_str(),"%d %d %d",&type,&code,&value)==3){input_event e{};e.type=type;e.code=code;e.value=value;write(u,&e,sizeof(e));e.type=EV_SYN;e.code=SYN_REPORT;e.value=0;write(u,&e,sizeof(e));}}
 if(observer>=0){input_event e;int n;while((n=read(observer,&e,sizeof(e)))==sizeof(e)){fprintf(log,"%d %d %d\n",e.type,e.code,e.value);fflush(log);}if(n<0&&errno==ENODEV){close(observer);observer=-1;}}
 usleep(10000);}
 if(observer>=0)close(observer);fclose(log);close(fifo);ioctl(u,UI_DEV_DESTROY);close(u);unlink(commands);return 0;}
