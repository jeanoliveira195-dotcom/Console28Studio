use console28_dsp::{Engine, Settings};
use std::f64::consts::PI;

fn sine(rate: u32, hz: f64, seconds: f64, level:f64) -> Vec<i16> {
    (0..(rate as f64 * seconds) as usize).map(|i| {
        (level * (2.0*PI*hz*i as f64/rate as f64).sin()*32767.0).round() as i16
    }).collect()
}
fn settings() -> Settings { Settings::default() }
fn run(signal: &[i16], s:Settings, rate:u32, chans:u32) -> Vec<i16> {
    let mut e=Engine::new(rate,chans).unwrap();
    e.set_settings(s);
    let mut out=signal.to_vec();
    e.process(&mut out).unwrap();
    out
}

#[test]
fn bypass_e_bit_exato() {
    let x=sine(48_000,1_000.0,0.3,0.9);
    assert_eq!(run(&x,Settings{enabled:false,..settings()},48_000,1),x);
}
#[test]
fn mix_zero_e_bit_exato() {
    let x=sine(48_000,1_000.0,0.3,0.9);
    assert_eq!(run(&x,Settings{mix:0.0,..settings()},48_000,1),x);
}
#[test]
fn modifica_sinal_musical() {
    let x=sine(48_000,440.0,0.2,0.45);
    assert_ne!(run(&x,settings(),48_000,1),x);
}
#[test]
fn silencio_permanece_silencio() {
    let x=vec![0_i16;48_000];
    assert_eq!(run(&x,settings(),48_000,1),x);
}
#[test]
fn canais_estereo_sao_isolados() {
    let mut input=vec![0_i16;9600];
    for i in (0..9600).step_by(2) {input[i]=12000;}
    let out=run(&input,Settings{mix:1.0,..settings()},48_000,2);
    assert!(out.iter().skip(1).step_by(2).all(|&v| v==0));
}
#[test]
fn chunks_produzem_mesmo_resultado_do_bloco_inteiro() {
    let x=sine(48_000,925.0,0.37,0.65);
    let whole=run(&x,settings(),48_000,1);
    let mut e=Engine::new(48_000,1).unwrap();
    let mut parts=x;
    for part in parts.chunks_mut(257) { e.process(part).unwrap(); }
    assert_eq!(whole,parts);
}
#[test]
fn taxas_44100_e_48000() {
    for rate in [44_100,48_000] {
        let x=sine(rate,1_000.0,0.1,0.60);
        let out=run(&x,settings(),rate,1);
        assert!(out.iter().any(|&v| v!=0));
        assert_eq!(out.len(),x.len());
    }
}
#[test]
fn suporta_mais_de_dois_canais_sem_vazamento() {
    let mut x=vec![0_i16;4*1024];
    for frame in 0..1024 { x[frame*4+3]=18000; }
    let out=run(&x,settings(),48_000,4);
    assert!(out.chunks(4).all(|f| f[0]==0 && f[1]==0 && f[2]==0));
}
#[test]
fn sem_overflow_na_saida_pcm() {
    let mut x=vec![0_i16;48_000];
    for (i,v) in x.iter_mut().enumerate() {*v=if i%2==0 {i16::MIN} else {i16::MAX};}
    let out=run(&x,Settings{drive:1.0,mix:1.0,output_db:6.0,..settings()},48_000,1);
    assert!(out.iter().all(|&v| (v as i32).abs() <= 32277)); // teto ~0.985
}
#[test]
fn bloqueio_de_dc_com_mix_integral() {
    let x=vec![12_000_i16;48_000];
    let out=run(&x,Settings{mix:1.0,..settings()},48_000,1);
    assert!(out[47_000..].iter().all(|&v| v.abs()<100));
}
#[test]
fn parametro_nao_finito_e_rejeitado() {
    let v=[0.2,0.5,0.5,f32::NAN,0.8,-0.7,65.0];
    assert!(Settings::from_wire(true,&v).is_none());
}
#[test]
fn reset_reproduz_igualmente_o_mesmo_trecho() {
    let x=sine(48_000,440.0,0.12,0.5);
    let mut e=Engine::new(48_000,1).unwrap();
    let mut first=x.clone();
    e.process(&mut first).unwrap();
    e.reset();
    let mut second=x;
    e.process(&mut second).unwrap();
    assert_eq!(first,second);
}
#[test]
fn aumenta_harmonicos_sem_injetar_sinal_ao_silencio() {
    // Frequência binária inteira para isolar uma harmônica no teste de projeção.
    let fs=48_000; let x=sine(fs,1_000.0,1.0,0.72);
    let out=run(&x,Settings{drive:0.85,warmth:0.5,presence:0.5,air:0.5,
        low_cut_hz:30.0,mix:1.0,output_db:0.0,enabled:true},fs,1);
    let mut sin2=0.0; let mut cos2=0.0;
    for (i,&v) in out.iter().enumerate().skip(24_000) {
        let phase=2.0*PI*2_000.0*i as f64/fs as f64;
        sin2 += v as f64*phase.sin(); cos2 += v as f64*phase.cos();
    }
    let amplitude=(sin2*sin2+cos2*cos2).sqrt()*2.0/24_000.0;
    assert!(amplitude>0.2, "2º harmônico esperado, medido={amplitude}");
}
#[test]
fn abi_c_cria_processa_e_destroi() {
    use console28_dsp::{console28_create,console28_destroy,console28_set_settings,console28_process};
    unsafe {
        let p=console28_create(48_000,1);
        assert!(!p.is_null());
        let settings=[0.18,0.65,0.60,0.46,0.82,-0.7,65.0];
        assert_eq!(console28_set_settings(p,1,settings.as_ptr(),settings.len()),0);
        let mut buffer=sine(48_000,500.0,0.05,0.6);
        assert_eq!(console28_process(p,buffer.as_mut_ptr(),buffer.len()),0);
        console28_destroy(p);
    }
}

fn rms_last_half(x:&[i16])->f64 {
    let y=&x[x.len()/2..];
    ((y.iter().map(|&a|(a as f64).powi(2)).sum::<f64>()) / y.len() as f64).sqrt()
}

#[test]
fn controle_de_calor_aumenta_graves_ao_girar() {
    let x=sine(48_000,150.0,0.6,0.35);
    let s=Settings{drive:0.0,mix:1.0,output_db:0.0,low_cut_hz:30.0,..settings()};
    let low=run(&x,Settings{warmth:0.1,..s},48_000,1);
    let high=run(&x,Settings{warmth:0.9,..s},48_000,1);
    assert!(rms_last_half(&high)>rms_last_half(&low)*1.15);
}

#[test]
fn controles_de_presenca_atuam_na_frequencia_desejada() {
    let x=sine(48_000,2_400.0,0.5,0.25);
    let s=Settings{drive:0.0,mix:1.0,output_db:0.0,low_cut_hz:30.0,..settings()};
    let low=run(&x,Settings{presence:0.1,..s},48_000,1);
    let high=run(&x,Settings{presence:0.9,..s},48_000,1);
    assert!(rms_last_half(&high)>rms_last_half(&low)*1.4);
}
